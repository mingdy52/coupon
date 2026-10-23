-- KEYS[1] = coupon:{id} (Hash: value, fetchedAtMs), KEYS[2] = lock key
-- ARGV: nowMs, freshMs, lockToken, lockTtlMs
-- 반환: HIT (값 반환) | STALE_REFRESH (값 반환 + 백그라운드 refresh)
--        | LOAD (이 요청이 채움) | WAIT (다른 요청이 채우는 중)

-- 조회, 신선도 판단, 락 획득을 한 스크립트로 묶어 다른 요청이 끼어들지 못하게 한다.

-- 1. 값과 저장 시각을 함께 읽는다
local data = redis.call('HMGET', KEYS[1], 'value', 'fetchedAtMs')
local value, fetchedAtStr = data[1], data[2]

-- 2. 값이 없음 (처음이거나 하드 TTL 만료): 응답할 값이 없으니 single flight
--    락을 잡은 요청 하나만 DB 를 조회하고, 나머지는 기다린다.
if not value then
    local acquired = redis.call('SET', KEYS[2], ARGV[3], 'NX', 'PX', ARGV[4])
    if acquired then return {'LOAD', ''} end
    return {'WAIT', ''}
end

-- 3. 값이 있고 아직 fresh: 그대로 반환
local now = tonumber(ARGV[1])
local fetchedAt = tonumber(fetchedAtStr)
if now - fetchedAt < tonumber(ARGV[2]) then
    return {'HIT', value}
end

-- 4. 값이 있지만 stale: 갱신 락을 시도한다. 어느 쪽이든 오래된 값으로 바로 응답한다.
--    락을 잡으면 이 요청이 백그라운드 갱신을 맡고, 못 잡으면 다른 요청이 갱신 중이니 그냥 HIT.
local acquired = redis.call('SET', KEYS[2], ARGV[3], 'NX', 'PX', ARGV[4])
if acquired then return {'STALE_REFRESH', value} end
return {'HIT', value}