#!/usr/bin/env bash
# 드레인 중 kill -9 유실 테스트. 사용: ./scripts/load/latency/kill9.sh
#
# 1. 리셋 + 쿠폰 생성 후 issue_burst 를 백그라운드로 실행
# 2. Redis 발급 수(SCARD)가 재고에 닿는 순간 = 200 응답이 모두 나간 시점에
#    DB 행 수를 기록하고 coupon-service 에 SIGKILL (kill -9 와 같음)
# 3. 재시작 후 드레인을 기다려 Redis 발급 수 - DB 행 수 = 유실 건수 확인

set -euo pipefail
cd "$(dirname "$0")/../../.."

TOTAL=5000
db_rows() {
  docker compose exec -T -e MYSQL_PWD=coupon mysql \
    mysql -ucoupon -BN coupon -e "SELECT COUNT(*) FROM issuance WHERE coupon_id = $COUPON_ID"
}
redis_issued() { docker compose exec -T redis redis-cli SCARD "coupon:$COUPON_ID:users"; }

./scripts/load/reset.sh
COUPON_ID=$(./scripts/load/create_coupon.sh)

k6 run -q -e COUPON_ID="$COUPON_ID" scripts/load/latency/issue_burst.js > /dev/null 2>&1 &
K6_PID=$!

until (( $(redis_issued) >= TOTAL )); do sleep 0.1; done
rows_at_kill=$(db_rows)
docker compose kill -s SIGKILL coupon-service
kill "$K6_PID" 2>/dev/null || true
printf '\n\033[1;31m===== kill -9: redis 발급 %s, DB 저장 %s (미저장 %s) =====\033[0m\n' \
  "$TOTAL" "$rows_at_kill" "$((TOTAL - rows_at_kill))"

docker compose start coupon-service
until [[ $(curl -s -o /dev/null -w '%{http_code}' "localhost:8080/api/coupons/$COUPON_ID") != 000 ]]; do sleep 1; done
printf '  재시작 완료\n'

# 행 수가 멈췄다고 드레인이 끝난 게 아니다. 죽은 컨슈머의 세션이 만료(session.timeout.ms 기본 45s)될 때까지
# 그룹이 리밸런싱에 묶여 아무것도 소비하지 않는다. 그래서 Redis 발급 수에 닿을 때까지(최대 120s) 기다린다.
for (( waited = 0; waited < 120; waited++ )); do
  (( $(db_rows) >= TOTAL )) && break
  sleep 1
done
printf '  재시작 후 %ss 만에 DB %s 건\n' "$waited" "$(db_rows)"

COUPON_ID="$COUPON_ID" ./scripts/load/latency/verify_burst.sh

issued=$(redis_issued); rows=$(db_rows)
printf '\n\033[1;36m===== 결과: redis 발급 %s, DB %s, 유실 %s =====\033[0m\n' "$issued" "$rows" "$((issued - rows))"
