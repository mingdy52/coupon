-- KEYS[1] = lock / ARGV[1] = lockToken
-- 내 token을 가진 락만 삭제한다.
-- GET 과 DEL 을 한 스크립트로 묶어, 확인과 삭제 사이에 락이 바뀌는 경우를 막는다.
-- 반환: 삭제했으면 1, 내 락이 아니면 (이미 만료됐거나 다른 요청 소유) 0
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0