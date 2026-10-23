# 부하 테스트

## 사전 준비

- `brew install k6 jq`
- `docker compose up -d` 후 서비스가 8080 응답

### 브랜치 전환 시
```bash
git switch <branch>
./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d
```

## efficiency

### 1. 쿠폰 정보 캐싱

쿠폰 정보를 Redis에 캐싱하면 조회를 Redis가 대부분 받아내므로 평균 DB 읽기 QPS가 크게 줄어든다.
캐시는 보통 TTL(예: 60초)을 두고 만료시킨다. 쿠폰 정보가 바뀔 수 있어 주기적으로 DB에서 다시 가져와야 하기 때문이다.

#### 문제: 캐시 스탬피드 (Cache Stampede)
- TTL이 만료되는 순간 같은 쿠폰 ID로 동시 요청이 몰리면, 캐시 미스가 난 요청이 **한꺼번에 DB로** 간다.
- 평균 부하는 낮아도 TTL 주기마다 스파이크가 생긴다 (동시 DB 요청 수천~수만 건).

#### 해법
| 방식 | 동작 | 트레이드오프 |
|---|---|---|
| **Single Flight** | 동시 캐시 미스 중 **한 요청(단일 로더)만 DB 조회**, 나머지는 결과가 캐시에 채워질 때까지 기다렸다 응답 | 대기 요청의 지연 증가 |
| **SWR** (Stale-While-Revalidate) | 오래된(stale) 값으로 **먼저 응답**하고, 백그라운드에서 DB를 조회해 캐시를 교체 | 갱신 전까지 DB와 캐시 값이 다를 수 있음 |

- SWR이라도 캐시가 너무 오래돼 완전히 만료(하드 TTL 초과)되면 다시 스탬피드가 발생한다 → 이 경우에 한해 Single Flight를 함께 둔다.

### 2. 매진 상태 캐싱

목표: 매진 이후 요청은 **Redis까지 가지 않고 L1에서 차단**한다.

1. Redis 매진 플래그: Redis 원자 연산에서 마지막 재고가 차감되는 순간 매진 플래그를 세팅 → 이후 요청은 플래그 확인 즉시 거절.
2. Caffeine L1 캐시: Redis 플래그 확인조차 부담일 수 있으므로 애플리케이션 로컬에 매진 여부를 캐싱.

주의점
- 매진인데 L1은 아직 모름 (짧은 불일치): 요청이 Redis까지 가지만, 원자 연산이 막아주므로 **정합성은 깨지지 않는다.**
- 매진이 해제되는 경우 (운영자 재고 추가, 보상 트랜잭션 등): L1에 남은 매진 값 때문에 **발급 가능한데도 거절**될 수 있다 → L1 무효화 전략 필요.

> 캐시는 빠르지만 항상 최신 값을 보장하지 않는다. **최종 안전장치(원자 연산) 와 무효화 전략**을 함께 설계해야 한다.

```bash
# 쿠폰 정보 조회
./scripts/load/efficiency/run.sh policy

# 매진 후 새로고침
./scripts/load/efficiency/run.sh sellout
```
`run.sh` 는 시나리오마다 `reset → 쿠폰 생성 → (매진 시나리오는 sell_out) → 카운터 리셋 → k6 → 카운터 조회` 를 워밍업 1회 + 본 측정 1회 실행한다.
`--once` 로 워밍업 생략.

### 실행 환경
- macOS 26.5.1, Apple M1 Pro, 32GB
- mysql:8.4 (리소스 제한 없음)
- Spring Boot 4.1.1 / JDK 25
- k6, 앱, DB 모두 같은 머신에서 실행

### 고정한 데이터·표본·명령
- 대상 API: `POST /api/coupons/{couponId}/issue`
- 매 실행 전 [`reset.sh`](../reset.sh) 로 `coupon`, `issuance` 테이블과 Redis 리셋

| 시나리오 | 쿠폰 준비 | k6 | 측정 |
|---|---|---|---|
| ① 쿠폰 정보 조회 급증 | [`create_issue_policy_coupon.sh`](create_issue_policy_coupon.sh) — 시작 시각 2099년 (NotStarted 로 끝남) | [`coupon_burst.js`](coupon_burst.js) — 500 req/s × 30s | `issue_policy_latency` p99, DB 조회 수 |
| ② 매진 후 새로고침 | [`create_small_coupon.sh`](create_small_coupon.sh) (재고 100) → [`sell_out.sh`](sell_out.sh) | [`post_sellout_refresh.js`](post_sellout_refresh.js) — 4,000 req/s × 30s | `issue_latency` p99 |

- DB 조회 수는 `GET /metrics/cache` 로 읽고 `POST /metrics/cache/reset` 으로 0 으로 만든다.
- `application.yaml` 캐시 설정은 모든 브랜치에서 같게 둔다.

| 설정 | 값 | 이유 |
|---|---|---|
| `coupon.cache.ttl-ms` | 1000 | 30초 측정 안에 만료가 30번 생겨, 스탬피드 구간(50ms / 1s = 5%)이 p99에 잡힌다. 10초면 0.5% 라 p99에 안 잡힘 |
| `coupon.cache.simulated-load-latency-ms` | 50 | 쿠폰 조회 시 DB 커넥션을 잡은 채 50ms 대기. 없으면 PK 조회가 1~2ms 라 DB가 병목이 되지 않음 |

## 결과

### 1. 캐시 없음 (기준선, `study3/efficiency-1-k6`)

#### 1-1. 1차: 지연 0ms (폐기)

| 지표 | 값 |
|---|---|
| 처리량 | 500 req/s (목표 500) |
| dropped_iterations | 0 |
| issue_policy_latency | avg 2.06ms / med 1.5ms / p95 4.38ms / **p99 11.51ms** / max 87.47ms |
| 처리된 요청 (http_reqs) | 15,002 |
| couponDbReads | 15,002 |
| couponCacheHits | 0 |
| DB 조회 비율 (couponDbReads / http_reqs) | 100% |

**1차의 문제점**
- **DB가 병목이 아님:** 모든 요청이 DB를 조회했지만 쿠폰 1건 PK 조회라 1~2ms 에 끝난다. 500 req/s 로는 DB가 버거워하지 않는다.
- **비교가 안 됨:** 캐시를 붙여도 2ms → 1ms 정도라 단계별(3-1 ~ 3-5) 차이가 드러나지 않는다.
- **결론:** `simulated-load-latency-ms` 를 50 으로 올려 무거운 DB 조회를 흉내 낸다.

#### 1-2. 2차: 지연 50ms, TTL 1s (기준선)

| 지표 | 1회 (TTL 10s) | 2회 (TTL 1s) |
|---|---|---|
| 처리량 | 182 req/s (목표 500) | 184 req/s (목표 500) |
| dropped_iterations | 8,521 | 8,501 |
| issue_policy_latency | avg 4.73s / med 4.8s / p95 7.49s / **p99 8.63s** / max 11.77s | avg 4.7s / med 4.49s / p95 7.5s / **p99 8.59s** / max 11.73s |
| 처리된 요청 (http_reqs) | 6,480 | 6,500 |
| couponDbReads | 6,480 | 6,500 |
| couponCacheHits | 0 | 0 |
| DB 조회 비율 (couponDbReads / http_reqs) | 100% | 100% |

**분석**
- **임계 실패:** p99 8.6s 로 목표 200ms 의 43배
- **병목은 DB 커넥션 풀:** `issue()` 는 `@Transactional` 이라 시작할 때 커넥션을 잡고, 그 상태로 50ms 를 기다린다. HikariCP 기본 풀 10개 ÷ 50ms = 이론상 최대 200 req/s, 실제 183 req/s 로 거의 일치한다.
- **목표 부하에 도달하지 못함:** 500 req/s 중 200 req/s 만 처리되고 나머지는 커넥션을 기다리며 줄을 선다. VU 1,000 개가 모두 대기에 묶여 `Insufficient VUs`, 약 8,500건은 보내지도 못했다.
- **모든 요청이 DB 조회:** DB 조회 비율 100%, 캐시 적중 0. 같은 쿠폰 정보를 매번 DB에서 다시 읽는다.
- **재현됨:** 두 번 측정한 결과 차이가 오차 범위 안이다.

### 2. Redis 캐시 (`study3/efficiency-2-redis-cache`)

쿠폰 발급 정책(`startsAt`, `validityDays`)만 `coupon:{id}:issue-policy` 키에 JSON 으로 캐싱한다 (cache-aside, TTL 1s).
캐시 미스일 때만 DB 조회와 50ms 지연이 일어난다. 동시 미스는 막지 않는다.

| 지표 | 캐시 없음 (TTL 1s) | Redis 캐시 |
|---|---|---|
| 처리량 | 184 req/s (목표 500) | **500 req/s** (목표 500) |
| dropped_iterations | 8,501 | 0 |
| issue_policy_latency | avg 4.7s / med 4.49s / p95 7.5s / **p99 8.59s** / max 11.73s | avg 3.89ms / med 1.37ms / p95 19.47ms / **p99 54.41ms** / max 109.52ms |
| 처리된 요청 (http_reqs) | 6,500 | 15,001 |
| couponDbReads | 6,500 | 280 |
| couponCacheHits | 0 | 14,721 |
| DB 조회 비율 (couponDbReads / http_reqs) | 100% | **1.87%** |

**분석**
- **목표 부하 처리:** 대부분의 요청이 Redis 에서 끝나 DB 커넥션을 오래 잡지 않는다. 500 req/s 를 모두 처리했고 dropped_iterations 도 0 이다.
- **스탬피드 발생:** 30초 동안 TTL 이 약 28~29번 만료된다. 만료마다 DB 조회 1번이면 충분한데 실제로는 평균 약 10번(280건) 조회했다. 만료 직후 동시에 미스 난 요청이 한꺼번에 DB 로 간다.
- **p99 54ms 가 스탬피드 구간:** 미스 비율 1.87% 가 p99 에 잡히고, 그 값이 지연 50ms + α 다. max 109ms 는 커넥션을 기다린 뒤 다시 50ms 를 기다린 요청으로 보인다.
- **스탬피드 규모를 커넥션 풀이 제한함 (추정):** 지연 50ms 동안 500 req/s × 50ms ≈ 25건이 들어오지만, `issue()` 가 `@Transactional` 이라 Redis 조회 전에 커넥션부터 잡는다. 풀이 10개라 동시에 미스를 낼 수 있는 요청도 최대 10건이고, 나머지는 커넥션을 기다리다 캐시가 채워진 뒤 적중한다. 측정된 DB 조회 수는 실제 스탬피드 규모보다 작게 잡혔을 수 있다.
