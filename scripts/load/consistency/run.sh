#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
source ./scripts/load/consistency/_common.sh

COUNT="${COUNT:-10}"

baseline() {
  for injector in force_dlt.sh force_db_only.sh; do
    ./scripts/load/reset.sh >/dev/null
    cid="$(./scripts/load/create_coupon.sh)"
    COUPON_ID="$cid" COUNT="$COUNT" "./scripts/load/consistency/$injector" >/dev/null
    COUPON_ID="$cid" ./scripts/load/consistency/drift_report.sh
  done
  printf '\n\033[1;32m===== consistency-1 불일치 주입 완료 =====\033[0m\n'
}

wait_dlt_id() {
  for _ in $(seq 1 30); do
    id="$(curl -fsS "$BASE/admin/issuance/dlt" | jq -r '.[0].id // empty')"
    [[ -n "$id" ]] && { printf '%s' "$id"; return 0; }
    sleep 1
  done
  return 1
}

dlt_replay() {
  local user_base=900000
  restart_service

  printf '\n\033[1;35m##### DLT replay #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT=1 USER_BASE="$user_base" ./scripts/load/consistency/force_dlt.sh >/dev/null
  user_id=$(( user_base + 1 ))
  dlt_id="$(wait_dlt_id)" || { ng "DLT 로그 대기 실패"; summary "consistency-2"; }
  curl -fsS -X POST "$BASE/admin/issuance/dlt/replay" \
    -H 'Content-Type: application/json' -d "{\"ids\":[$dlt_id]}" >/dev/null
  issued_query="SELECT COUNT(*) FROM issuance WHERE user_id=$user_id AND coupon_id=$cid"
  for _ in $(seq 1 30); do
    [[ "$(mysql_scalar "$issued_query")" == "1" ]] && break
    sleep 1
  done
  check "원래 발급으로 DB 저장" "$(mysql_scalar "$issued_query")" "1"
  check "DLT 로그 상태" \
    "$(mysql_scalar "SELECT status FROM issuance_dlt_log WHERE id=$dlt_id")" "REPLAYED"
  summary "consistency-2"
}

# 대사 대상: coupon:reconcile:recent ZSET 에 발급 시각(score)으로 등록된 쿠폰 중
# 배치 실행 시각 기준 10초 이전에 발급된 것만 본다. (배치 주기 1분)
# 10초는 Redis 재고 차감 → Kafka 이벤트 → DB 저장까지 한 건의 발급이 끝나는 시간을
# 넉넉히 잡은 값. 그 안의 쿠폰은 아직 처리 중일 수 있어 제외한다.

reconcile() {
  restart_service 3600000

  printf '\n\033[1;35m##### 최근 대사 대상 등록 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  curl -fsS -X POST "$BASE/api/coupons/$cid/issue" -H 'X-User-Id: 800001' >/dev/null
  [[ -n "$(redis_cli ZSCORE coupon:reconcile:recent "$cid")" ]] && pass "발급 쿠폰을 최근 대사 대상으로 등록" || ng "발급 쿠폰을 최근 대사 대상으로 등록"
  issued_query="SELECT COUNT(*) FROM issuance WHERE coupon_id=$cid AND user_id=800001"
  for _ in $(seq 1 30); do
    [[ "$(mysql_scalar "$issued_query")" == "1" ]] && break
    sleep 1
  done
  check "발급 DB 저장" "$(mysql_scalar "$issued_query")" "1"

  printf '\n\033[1;35m##### Redis users 누락 자동 보정 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/consistency/force_db_only.sh >/dev/null
  result="$(curl -fsS -X POST "$BASE/admin/reconcile/run")"
  check "발급자 명단 복구" "$(redis_cli SCARD "coupon:$cid:users")" "$COUNT"
  check "자동 보정 횟수" "$(jq -r '.autoFixed' <<< "$result")" "1"

  printf '\n\033[1;35m##### DB 측 불일치는 알람만 #####\033[0m\n'
  ./scripts/load/reset.sh >/dev/null
  cid="$(./scripts/load/create_coupon.sh)"
  COUPON_ID="$cid" COUNT="$COUNT" ./scripts/load/consistency/force_dlt.sh >/dev/null
  stock_before="$(redis_cli GET "coupon:$cid:stock")"
  result="$(curl -fsS -X POST "$BASE/admin/reconcile/run")"
  check "DB 측 불일치 감지" "$(jq -r '.redisDbDrift' <<< "$result")" "$COUNT"
  check "Redis 재고 유지" "$(redis_cli GET "coupon:$cid:stock")" "$stock_before"
  summary "consistency-3"
}

source_stage() {
  if [[ -f src/main/java/com/mingdy/coupon/batch/Reconciler.java ]]; then
    printf 'consistency-3'
  elif [[ -f src/main/java/com/mingdy/coupon/application/IssuanceDltService.java ]]; then
    printf 'consistency-2'
  else
    printf 'consistency-1'
  fi
}

runtime_stage() {
  reconcile_status="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/admin/reconcile/run")"
  if [[ "$reconcile_status" == "405" ]]; then
    printf 'consistency-3'
  elif curl -fsS "$BASE/admin/issuance/dlt" >/dev/null 2>&1; then
    printf 'consistency-2'
  else
    printf 'consistency-1'
  fi
}

wait_service_ready || { printf 'coupon-service 준비 실패\n' >&2; exit 1; }

source="$(source_stage)"
runtime="$(runtime_stage)"
if [[ "$source" != "$runtime" ]]; then
  printf '현재 소스는 %s이지만 실행 중인 coupon-service는 %s입니다.\n' "$source" "$runtime" >&2
  printf './gradlew jibDockerBuild 후 docker compose up -d --force-recreate coupon-service를 실행하세요.\n' >&2
  exit 1
fi

case "$source" in
  consistency-3) reconcile ;;
  consistency-2) dlt_replay ;;
  consistency-1) baseline ;;
esac
