#!/usr/bin/env bash
set -e
BASE="${BASE_URL:-http://localhost:8080}"

COUPON=$(curl -sS -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d '{"name":"여름 할인","totalQuantity":100,"validityDays":7}' | jq -r '.id')

# 최초 한 번 등록하고, 입장권이 나올 때까지 상태만 조회한다.
ADMITTED=$(curl -sS -X POST "$BASE/api/waiting-room/$COUPON" -H 'X-User-Id: 1' | jq -r '.admitted')
for _ in $(seq 1 10); do
  [ "$ADMITTED" = "true" ] && break
  sleep 1
  ADMITTED=$(curl -sS "$BASE/api/waiting-room/$COUPON" -H 'X-User-Id: 1' | jq -r '.admitted')
done
[ "$ADMITTED" = "true" ] || { echo "대기실 입장권을 받지 못했습니다" >&2; exit 1; }

curl -sS -X POST "$BASE/api/coupons/$COUPON/issue" -H 'X-User-Id: 1' >/dev/null

# 발급은 비동기 저장이라 응답에 id 가 없다. 목록에서 저장된 id 를 받아 쓴다.
for _ in $(seq 1 10); do
  ISSUANCE=$(curl -sS "$BASE/api/users/me/issuances" -H 'X-User-Id: 1' | jq -r '.[0].id // empty')
  [ -n "$ISSUANCE" ] && break
  sleep 1
done
[ -n "$ISSUANCE" ] || { echo "발급 내역이 저장되지 않았습니다" >&2; exit 1; }

curl -sS -X POST "$BASE/api/issuances/$ISSUANCE/use" -H 'X-User-Id: 1'
curl -sS "$BASE/api/users/me/issuances" -H 'X-User-Id: 1'