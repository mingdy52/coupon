-- 발급 원자 처리. Redis 단일 스레드 + 스크립트 단위 atomic 으로
-- 재고 차감 + 사용자 추가 를 한 덩어리에 묶는다.
--
-- KEYS[1] = coupon:{id}:stock   (재고 카운터, 행사 생성 시 totalQuantity 로 초기화)
-- KEYS[2] = coupon:{id}:users   (발급된 사용자 set)
-- KEYS[3] = coupon:{id}:sold_out
-- ARGV[1] = user_id
-- ARGV[2] = soldOutTtlSeconds
-- 반환:
--   1   발급 성공
--   0   매진
--  -1   이미 발급된 사용자

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return -1
end

local remaining = tonumber(redis.call('GET', KEYS[1]) or '0')
if remaining <= 0 then
  return 0
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
-- 대사 sliding window 인덱스: 쿠폰별 최신 발급 시각을 score 로 (member 는 couponId, ZADD 가 갱신).
redis.call('ZADD', KEYS[4], ARGV[3], ARGV[4])

-- 차감 전 재고가 1 이었다면 이 요청이 마지막 재고를 가져간 것 → 매진 플래그 세팅.
-- 재고 차감과 같은 스크립트 안이라 "재고 0 인데 플래그 없음" 같은 중간 상태가 보이지 않는다.
-- EX: 초 단위 만료 시간. 이벤트가 끝나면 플래그가 저절로 사라진다.
if remaining == 1 then
  redis.call('SET', KEYS[3], '1', 'EX', ARGV[2])
end

return 1