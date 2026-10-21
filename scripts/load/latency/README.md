# 부하 테스트

### 브랜치 전환 시
```bash
git switch <branch>
./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d
```

## latency
[](latency.png)
redis에서 발급 판정을 마치고 쿠폰 발급 이벤트를 적재한다. 그리고 200 응답을 반환한다.
Worker가 항상 별도로 존재하고 이벤트가 들어오면 그 메시지를 소비해서 쿠폰 발급 DB 작업을 실행한다.

> Worker가 책임
> 트랜잭션: Issuance 저장과 Coupon 증가
> 원자 증가: 여러 Worker가 lost update방지. Worker를 여러개 둘 경우 그 Worker가 그 메세지를 동시에 소비하게 되면서 경쟁 상태에 놓이게 될 수 있다.
> 멱등 처리: UNIQUE 중복은 성공으로 흡수

### 동기 DB의 한계
DB 부하가 커지면 요청 스레드가 DB 작업을 기다려 사용자 응답이 느려질 수 있다.
큐를 분리하면 발급 이벤트는 큐에 넣고 바로 응답하고 Worker가 뒤에서 쿠폰 db 저장을 처리한다.

```bash
./scripts/load/latency/run.sh
```

### 실행 환경
- macOS 26.5.1, Apple M1 Pro, 32GB
- mysql:8.4 (리소스 제한 없음)
- Spring Boot 4.1.1 / JDK 25
- k6, 앱, DB 모두 같은 머신에서 실행

### 고정한 데이터·표본·명령
- 쿠폰: `total_quantity = 5000`, 매 회차 전 `coupon`, `issuance` 테이블과 Redis 리셋
- 사용자: `X-User-Id` 를 1~20,000 중 랜덤 (같은 사용자 중복 요청 발생)
- 대상 API: `POST /api/coupons/{couponId}/issue`
- k6 시나리오: [`issue_burst.js`](issue_burst.js) — `constant-arrival-rate` `RATE` req/s × 30s (VU 2,000~`MAX_VUS`)
  - 1차: `RATE=5000` (폐기)
  - 2차: `RATE=3000`, `MAX_VUS=15000` (폐기)
  - 3차: `RATE=3000`, `MAX_VUS=5000` 으로 고정 (기준선)
- 실행: 워밍업 1회 후 본 측정 1회, 본 측정 값만 기록
- 기대 결과: `issue_latency` p99 < 500ms, `status_other = 0`, 정합성 유지


### 판정 기준 [`verify_burst.sh`](verify_burst.sh)
Worker 가 큐를 다 비울 때까지(발급 수가 3초간 변하지 않을 때까지) 기다린 뒤 확인한다.

| 항목 | 의미 | 통과 조건 |
|---|---|---|
| `issue_latency` | 사용자 응답 시간 | `p(99) < 500ms` |
| `conn_reset` | 서버가 연결을 끊은 요청 수 (레이턴시에서 제외) | `0` |
| `over_issuance` | 재고보다 많이 발급됐는가 | `issuance_rows <= total_quantity` |
| `count_match` | 카운터 필드가 실제 발급 수와 맞는가 | `issued_quantity = issuance_rows` |


## 결과

### 1. 동기 (기준선, `study2/latency-1-k6`)

#### 1-1. 1차: `RATE=5000` (폐기)

| issued_quantity | total_quantity | issuance_rows | over_issuance | count_match |
|---:|---:|---:|:---:|:---:|
| 4,996 | 5,000 | 4,996 | OK | OK |

| 지표 | 값 |
|---|---|
| 처리량 | 3,560 req/s (목표 5,000) |
| dropped_iterations | 43,178 |
| issue_latency (전체) | avg 814ms / med 372ms / p95 4.66s / **p99 7.24s** / max 8.04s |
| 성공 요청 latency | avg 4.76s / med 5.02s / p95 7.56s / max 7.87s |
| status_2xx | 4,996 |
| status_409 (매진·중복) | 101,823 |
| status_other | 4 |

**분석**
- **임계 실패:** p99 7.24s 로 목표 500ms 의 14배
- **목표 부하에 도달하지 못함:** VU 5,000 개가 응답 대기에 묶여 `Insufficient VUs`, 43,178건은 보내지도 못했따.
- **발급 성공자가 가장 오래 기다림:** 성공 요청 중간값 5.02s. 매진 응답은 빠르지만 실제로 쿠폰을 받는 사용자는 DB 저장(중복 확인, UPDATE, INSERT, 커밋)이 끝날 때까지 기다린다.
- **전체 중간값이 낮은 이유:** 요청의 95%가 매진 응답이라 빠르게 끝나 평균을 끌어내린다. 응답성은 성공 요청과 p99 로 봐야 한다.
- **과소 발급 4건:** `status_other` 4건과 일치. 같은 사용자 동시 요청이 UNIQUE 제약에 걸려 500, Redis 재고는 차감된 채 남음 (concurrency 3번과 같은 원인)

**1차의 문제점**
- **브랜치마다 받는 부하가 달라짐:** 동기는 목표의 71%(3,560 req/s)만 받았다. 비동기가 5,000 을 다 받으면 서로 다른 부하에서 레이턴시를 비교하게 된다.
- **p99 가 실제 지연보다 낮게 나옴:** VU 5,000 개가 응답 대기에 묶여 43,178건을 보내지 못했다. 보내지 못한 요청은 레이턴시에 기록되지 않아 실제보다 좋게 보인다.
- **k6 가 앱의 CPU 를 빼앗음:** k6, 앱, DB 가 한 머신에서 돌아 VU 5,000 개를 돌리는 k6 부하가 앱 응답 시간에 섞인다.
- **결론:** 동기 방식이 `dropped_iterations` 없이 버티는 rate 로 낮춰 모든 브랜치에 같은 부하를 준다. → `RATE` 환경변수로 변경

#### 1-2. 2차: `RATE=3000`, `MAX_VUS=15000` (폐기)

**1차에서 바꾼 것:** rate 5,000 → 3,000, VU 최대 5,000 → 15,000, 연결 끊김(`conn_reset`)을 레이턴시에서 제외

| issued_quantity | total_quantity | issuance_rows | over_issuance | count_match |
|---:|---:|---:|:---:|:---:|
| 4,998 | 5,000 | 4,998 | OK | OK |

| 지표 | 값 |
|---|---|
| 처리량 | 1,058 req/s (목표 3,000) |
| dropped_iterations | 26,135 |
| conn_reset | 5,185 |
| issue_latency (전체) | avg 2.17s / med 351ms / p95 10.18s / **p99 10.63s** / max 11.33s |
| 성공 요청 latency | avg 5.74s / med 5.92s / p95 10.36s / max 11s |
| status_2xx | 4,998 |
| status_409 (매진·중복) | 53,578 |
| status_other | 2 |
| 소요 시간 | 1m00s (30s 시나리오 + graceful stop 30s, 102건 중단) |

**2차의 문제점**
- **VU 를 늘려도 드롭이 남음:** 최대 15,000 을 허용했지만 실제로는 8,303 개까지만 만들어졌고 26,135건은 보내지 못했다.
- **서버가 연결을 끊음:** Tomcat 기본 한도(`max-connections` 8,192 + `accept-count` 100)를 넘는 연결 5,185건이 `cnnection reset by peer`
- **오히려 나빠짐:** 보낸 요청 수 `73,028 → 63,763`, `p99 6.19s → 10.63s`. k6 가 VU 8,000 개를 돌리느라 같은 머신의 앱이 CPU 를 덜 받는다.
- **결론:** 동기 방식은 VU 를 늘려도 다 받지 못한다. `MAX_VUS=5000` 으로 되돌려 모든 브랜치에 같은 설정을 주고, `dropped_iterations`, `conn_reset` 도 결과 지표로 함께 비교한다.

#### 1-3. 3차: `RATE=3000`, `MAX_VUS=5000` (기준선)

**2차에서 바꾼 것:** VU 최대 15,000 → 5,000 으로 되돌림.

| issued_quantity | total_quantity | issuance_rows | over_issuance | count_match |
|---:|---:|---:|:---:|:---:|
| 4,997 | 5,000 | 4,997 | OK | OK |

| 지표 | 값 |
|---|---|
| 처리량 | 2,458 req/s (목표 3,000) |
| dropped_iterations | 16,255 |
| conn_reset | 0 |
| issue_latency (전체) | avg 577ms / med 1.59ms / p95 4.72s / **p99 5.93s** / max 6.33s |
| 성공 요청 latency | avg 3.6s / med 3.94s / p95 5.9s / max 6.2s |
| status_2xx | 4,997 |
| status_409 (매진·중복) | 68,746 |
| status_other | 3 |

**분석**
- **임계 실패:** p99 5.93s 로 목표 500ms 의 약 12배
- **발급 성공자가 가장 오래 기다림:** 성공 요청 중간값 3.94s. 매진 응답 중간값은 1.59ms 로, 쿠폰을 받는 요청만 DB 저장을 기다린다.
- **폭증 구간에 VU 가 묶임:** 발급 요청이 4초씩 VU 를 잡아 5,000 개가 모두 소진, 16,255건(18%)은 보내지 못했다. 재고가 매진된 뒤에는 응답이 빨라져 VU 가 다시 남는다.
- **연결 끊김 없음:** Tomcat 연결 한도(8,192) 안이라 `conn_reset = 0`
- **과소 발급 3건:** `status_other` 3건과 일치
- **비동기 브랜치에서 볼 것:** 성공 요청 latency, p99, `dropped_iterations` 가 줄어드는가
