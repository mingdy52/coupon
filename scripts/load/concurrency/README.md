# 부하 테스트

## 사전 준비

- `brew install k6 jq`
- `docker compose up -d` 후 서비스가 8080 응답

### 브랜치 전환 시
```bash
git switch <branch>
./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d
```

## concurrency

```bash
./scripts/load/concurrency/run.sh
```
`run.sh` 는 `reset → create_coupon → k6 → verify` 를 한 번에 실행한다.

### 실행 환경
- macOS 26.5.1, Apple M1 Pro, 32GB
- mysql:8.4 (리소스 제한 없음)
- Spring Boot 4.1.1 / JDK 25
- k6, 앱, DB 모두 같은 머신에서 실행

### 고정한 데이터·표본·명령
- 쿠폰: `total_quantity = 5000`, 매 실행 전 `coupon`, `issuance` 테이블 리셋
- 사용자: `X-User-Id` 를 1~20,000 중 랜덤 (같은 사용자 중복 요청 발생)
- 대상 API: `POST /api/coupons/{couponId}/issue`
- k6 시나리오: [`over_issuance.js`](over_issuance.js) —  VU 500개가 총 10,000건 요청
- 기대 결과: 정확히 5,000건 발급, `duplicate_users = 0`


### 판정 기준 [`verify.sh`](verify.sh)
| 항목 | 의미 | 통과 조건 |
|---|---|---|
| `over_issuance` | 재고보다 많이 발급됐는가 | `issuance_rows <= total_quantity` |
| `count_match` | 카운터 필드가 실제 발급 수와 맞는가 | `issued_quantity = issuance_rows` |
| `duplicate_users` | 같은 사용자가 2번 이상 받았는가 | `0` |


## 결과

### 1. 락 없음 (기준선, `study1/concurrency-1-k6`)

#### 1-1. 1차: 고부하 (`constant-arrival-rate` 5,000 req/s)

- `constant-arrival-rate` 5,000 req/s × 30s, VU 2,000~5,000
- 사용자 `X-User-Id` 1~20,000 중 랜덤 (같은 사용자 중복 요청 발생)

| issued_quantity | total_quantity | issuance_rows | duplicate_users | over_issuance | count_match |
|---:|---:|---:|---:|:---:|:---:|
| 1,536 | 5,000 | 14,763 | 0 | FAIL | FAIL |

| 지표 | 값 |
|---|---|
| 처리량 | 823 req/s (목표 5,000) |
| http_req_duration | avg 5.64s / med 4.69s / p95 9.84s / max 13.55s |
| http_req_failed | 45.25% (12,204 / 26,967) |
| dropped_iterations | 123,035 |

**분석**
- **과발급:** 재고 5,000장인데 14,763장이 발급됐다.
- **카운터 유실:** `issued_quantity` 는 1,536 으로 실제 발급 수의 약 10%만 반영됐다.
- **중복 발급 없음:** 같은 사용자의 동시 요청이 있었지만 UNIQUE 제약(`uk_issuance_user_coupon`)이 DB 단에서 막았다.

**1차의 문제점**
- **목표 부하에 도달하지 못함:** VU 5,000 개가 모두 응답 대기에 묶여 `Insufficient VUs` 경고가 났다. 실제 부하는 목표의 16%인 823 req/s 였고, 123,035건은 보내지도 못했다.
- **과부하가 결과를 덮음:** p95 9.84s 로 요청 대부분이 줄 서서 기다렸다.
- **실패 원인을 구분할 수 없음:** 45% 실패에 이미 발급된 사용자의 거절과 과부하 지연이 섞여 있다.
- **고부하가 필요 없음:** 실제로 같은 쿠폰 행을 동시에 건드리는 요청 수는 DB 커넥션 풀(기본 10개)로 제한된다. 서버가 감당하는 부하에서도 경쟁 조건은 똑같이 재현된다.

#### 1-2. 2차: 적정 부하 (`shared-iterations` VU 500 × 10,000건)

**1차에서 바꾼 것**

| 항목 | 1차 | 2차 |
|---|---|---|
| executor | `constant-arrival-rate` 5,000 req/s × 30s | `shared-iterations` 총 10,000건 |
| VU | 2,000~5,000 | 500 (커넥션 풀 10개를 채우기에 충분) |
| 사용자 | 1~20,000 랜덤 | 1~20,000 랜덤 (1인 1매 검증을 위해 중복 유지) |
| 측정 기준 | 정해진 시간 동안 최대한 많이 | 정해진 요청 수를 끝까지 처리 |

| issued_quantity | total_quantity | issuance_rows | duplicate_users | over_issuance | count_match |
|---:|---:|---:|---:|:---:|:---:|
| 793 | 5,000 | 7,840 | 0 | FAIL | FAIL |

| 지표 | 값 |
|---|---|
| 소요 시간 | 11.5s |
| 처리량 | 868 req/s |
| http_req_duration | avg 560ms / med 559ms / p95 713ms / max 964ms |
| http_req_failed | 21.60% (2,160 / 10,000) |

**분석**
- **과부하:** p95 가 9.84s → 713ms 로 줄었다. 서버가 감당하는 범위에서 측정해 결과가 경쟁 조건만 반영한다.
- **과발급:** 재고 5,000장인데 7,840장이 발급됐다. 매진 응답은 한 건도 없었다.
- **카운터 유실:** `issued_quantity` 는 793 으로 실제 발급 수의 약 10%만 반영됐다.
- **과발급의 원인:** 카운터 유실. 카운터가 5,000에 한 번도 도달하지 못해서 `isSoldOut()` 이 끝까지 `false` 였다.
- **실패 원인:** 2,160건의 실패는 모두 이미 발급된 사용자의 재요청이다.`10,000 - 7,840`

### 2. 비관적 락 (`study1/concurrency-2-pessimistic`)

**기준선에서 바꾼 것**
- 쿠폰 조회를 `@Lock(PESSIMISTIC_WRITE)` 로 변경 → `SELECT ... FOR UPDATE`
- 같은 쿠폰에 대한 발급 트랜잭션이 행 락을 잡고 하나씩 순서대로 실행된다.
- k6 시나리오, 고정 데이터는 기준선 2차와 동일

| issued_quantity | total_quantity | issuance_rows | duplicate_users | over_issuance | count_match |
|---:|---:|---:|---:|:---:|:---:|
| 5,000 | 5,000 | 5,000 | 0 | OK | OK |

| 지표 | 값 | 기준선 2차 대비 |
|---|---|---|
| 소요 시간 | 16.2s | 11.5s → 16.2s (+41%) |
| 처리량 | 617 req/s | 868 → 617 req/s (-29%) |
| http_req_duration | avg 802ms / med 932ms / p95 1.7s / max 2.96s | p95 713ms → 1.7s (2.4배) |
| 성공 요청 duration | avg 1.25s / med 1.14s / p95 1.83s / max 2.95s | - |
| http_req_failed | 50.00% (5,000 / 10,000) | 21.60% → 50.00% |

**분석**
- **과발급 차단:** 재고 5,000장에 정확히 5,000장이 발급
- **카운터 정합성:** `issued_quantity` 와 실제 발급 수가 일치
- **중복 발급:** 사용자 확인도 락 안에서 실행돼 같은 사용자의 요청이 순서대로 처리
- **실패 원인:** 5,000건의 실패는 매진 응답과 이미 발급된 사용자의 재요청이다. `10,000 - 5,000`
- **비용:** 모든 발급 요청이 쿠폰 행 하나에서 줄을 선다. 처리량이 29% 줄고 p95 는 2.4배 늘었다. 락을 기다리는 동안 DB 커넥션도 붙잡고 있어 다른 API까지 느려질 수 있다.


### 3. Redis Lua (`study1/concurrency-3-redis-lua`)

**기준선에서 바꾼 것**
- 쿠폰 생성 시 Redis 에 재고 키(`coupon:{id}:stock`)를 초기화
- 발급 시 Lua 스크립트로 재고 확인 + 차감을 원자적으로 실행 → 통과한 요청만 DB 발급
- `issued_quantity` 증가를 엔티티 변경 대신 `UPDATE ... SET issued_quantity = issued_quantity + 1` 로 변경
- k6 시나리오, 고정 데이터는 기준선 2차와 동일

| issued_quantity | total_quantity | issuance_rows | duplicate_users | over_issuance | count_match |
|---:|---:|---:|---:|:---:|:---:|
| 4,997 | 5,000 | 4,997 | 0 | OK | OK |

| 지표 | 값 | 비관적 락 대비 |
|---|---|---|
| 소요 시간 | 13.4s | 16.2s → 13.4s (-17%) |
| 처리량 | 744 req/s | 617 → 744 req/s (+21%) |
| http_req_duration | avg 667ms / med 757ms / p95 1.32s / max 4.37s | p95 1.7s → 1.32s (-22%) |
| 성공 요청 duration | avg 1.03s / med 947ms / p95 1.71s / max 4.37s | p95 1.83s → 1.71s |
| http_req_failed | 50.02% (5,003 / 10,000) | 50.00% → 50.02% |

**분석**
- **과발급 차단:** Redis 재고가 0 이 된 뒤 요청은 모두 거절
- **카운터 정합성:** 원자적 UPDATE 로 `issued_quantity` 와 실제 발급 수가 일치
- **중복 발급:** UNIQUE 제약이 막아 `duplicate_users = 0`
- **과소 발급 3건:** Redis 재고는 0 인데 DB 발급은 4,997건
  - 같은 사용자의 동시 요청 2건이 모두 중복 확인(`exists`)을 통과하고 Redis 재고를 차감
  - 한 건이 INSERT 에서 UNIQUE 제약에 걸려 DB 는 롤백, Redis 재고는 차감된 채 남음
  - 앱 로그의 `Duplicate entry` 3건과 일치
  ```bash
  # Redis 남은 재고 → 0 (5,000장 모두 통과)
  docker compose exec -T redis redis-cli get coupon:1:stock

  # UNIQUE 제약 위반 → 3건 ('사용자ID-쿠폰ID')
  docker compose logs coupon-service | grep -o "Duplicate entry '[0-9-]*'" | sort -u
  ```
- **속도 향상이 크지 않음:** 재고 판단은 Redis 로 옮겼지만 요청마다 DB 작업(조회, 중복 확인, UPDATE, INSERT)은 그대로다. UPDATE 가 잡은 쿠폰 행 락은 커밋까지 유지돼 INSERT 동안에도 다른 발급이 대기한다.
- **해결 방법:** 실패 시 Redis 재고를 되돌리는 보상 처리, 중복 확인도 Redis 로 옮기기(`SADD`)


## 비교

| 방식 | 막는 위치 | 줄 서는 구간 | 재고 판단 기준 | 장점 | 남는 비용 |
|---|---|---|---|---|---|
| DB 비관락 | 쿠폰 조회(`FOR UPDATE`) ~ 커밋 | 트랜잭션 전체 | DB `issued_quantity` | 추가 인프라 없이 정확함 | 락 대기 동안 DB 커넥션 점유, 다른 API 까지 느려질 수 있음 |
| Redis 분산 락 | 락 획득 ~ 해제 | 발급 구간 전체 (임계영역이 넓음) | DB `issued_quantity` | DB 행 락 부담 감소, 여러 서버·자원을 하나로 묶을 수 있음 | 락 획득/해제 왕복, 락 만료·해제 실패 처리 필요 |
| Redis 원자 연산 (Lua) | DB 쓰기 전 | Lua 스크립트 1회 (+ UPDATE ~ 커밋) | Redis 재고 키 | 락 대기 없이 성공 수를 재고만큼 제한 | DB 저장은 동기, Redis ↔ DB 불일치 시 보상 처리 필요 |

### 측정 결과

| 항목 | 락 없음 (2차) | DB 비관락 | Redis 원자 연산 (Lua) |
|---|---:|---------------:|--:|
| 발급 수 / 재고 | 7,840 / 5,000 |  5,000 / 5,000 | 4,997 / 5,000 |
| over_issuance | FAIL |             OK | OK |
| count_match | FAIL |             OK | OK |
| 소요 시간 | 11.5s |          16.2s | 13.4s |
| 처리량 | 868 req/s |      617 req/s | 744 req/s |
| p95 | 713ms |           1.7s | 1.32s |

### 정리
- **락 없음:** 가장 빠르지만 카운터 유실로 과발급
- **DB 비관락:** 정확하지만 모든 요청이 쿠폰 행 하나에서 줄을 서서 가장 느림
- **Redis 원자 연산:** 비관락보다 처리량 +21%, p95 -22%. 단 DB 실패 시 Redis 재고가 복구되지 않아 과소 발급 3건

> Lua 에 중복 확인(`SADD`)과 보상 처리를 넣어 과소 발급을 없애고, DB 저장을 비동기로 분리하면 남은 대기 구간도 줄일 수 있음
