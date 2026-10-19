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



