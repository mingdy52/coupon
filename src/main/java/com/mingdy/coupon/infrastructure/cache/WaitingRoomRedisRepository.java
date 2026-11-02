package com.mingdy.coupon.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.mingdy.coupon.infrastructure.cache.RedisLuaSupport.*;

@Repository
@RequiredArgsConstructor
public class WaitingRoomRedisRepository {

    // 대기실이 열린 쿠폰 ID 집합. 배출 타이머가 순회한다.
    private static final String ROOMS_KEY = "waiting:rooms";

    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterScript = listLuaScript("lua/waiting-room-enter.lua");
    private final RedisScript<Long> statusScript = longLuaScript("lua/waiting-room-status.lua");
    private final RedisScript<Long> drainScript = longLuaScript("lua/waiting-room-drain.lua");

    // (통과 여부, 1-based 순번). 통과면 0.
    public WaitingRoomEntry enter(long couponId, long userId) {
        // 먼저 활성화해야 중간 장애가 나도 대기열이 배출 대상에서 빠지지 않는다.
        redisTemplate.opsForSet().add(ROOMS_KEY, String.valueOf(couponId));
        List<String> result = runForStrings(
                redisTemplate,
                enterScript,
                List.of(queueKey(couponId), passKey(couponId, userId)),
                userId
        );
        return new WaitingRoomEntry(result.get(0).equals("1"), Long.parseLong(result.get(1)));
    }

    // 반환: 0=통과, 1-based 순번=대기, null=아직 진입하지 않음.
    public Long status(long couponId, long userId) {
        long position = runForLong(
                redisTemplate,
                statusScript,
                List.of(queueKey(couponId), passKey(couponId, userId)),
                userId
        );
        return position < 0 ? null : position;
    }

    // 입장권 키가 있으면 통과. 발급해도 지우지 않고 TTL 로 만료시킨다.
    public boolean isAdmitted(long couponId, long userId) {
        return redisTemplate.hasKey(passKey(couponId, userId));
    }

    // 반환: 이번 틱에 통과시킨 인원 수.
    public int drain(long couponId, int admitPerSecond, long passTtlMs) {
        return (int) runForLong(
                redisTemplate,
                drainScript,
                List.of(queueKey(couponId)),
                admitPerSecond, passTtlMs, passKeyPrefix(couponId)
        );
    }

    // 줄이 비어도 집합에서 지우지 않는다. 빈 대기실은 drain 에서 0명으로 끝난다.
    public List<Long> activeRooms() {
        Set<String> members = redisTemplate.opsForSet().members(ROOMS_KEY);
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(WaitingRoomRedisRepository::toLongOrNull)
                .filter(Objects::nonNull)
                .toList();
    }

    // {couponId} 해시 태그로 한 쿠폰의 키들을 같은 슬롯에 모은다 (Lua 멀티키 안전).
    private String queueKey(long couponId) {
        return "waiting:{" + couponId + "}:queue";
    }

    private String passKeyPrefix(long couponId) {
        return "waiting:{" + couponId + "}:pass:";
    }

    private String passKey(long couponId, long userId) {
        return passKeyPrefix(couponId) + userId;
    }

    // 숫자가 아니면 null. 형식이 깨진 값을 걸러내는 용도.
    private static Long toLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
