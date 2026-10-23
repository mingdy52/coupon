-- KEYS: cache, lock / ARGV: lockToken, lockTtlMs
-- 반환: HIT | LOAD | WAIT (대기 필요)
-- 캐시 조회와 락 획득 사이에 다른 요청이 끼어들지 못하도록 한 스크립트로 묶는다.

-- 1. 캐시에 값이 있으면 그대로 반환
local cached = redis.call('GET', KEYS[1])
if cached then return {'HIT', cached} end

-- 2. 캐시가 비었으면 락을 시도한다. NX: 키가 없을 때만 SET, PX: 밀리초 TTL
--    처음 도착한 요청만 성공해 DB 로드를 맡고, 나머지는 WAIT 로 대기한다.
local acquired = redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2])
if acquired then return {'LOAD', ''} end
return {'WAIT', ''}