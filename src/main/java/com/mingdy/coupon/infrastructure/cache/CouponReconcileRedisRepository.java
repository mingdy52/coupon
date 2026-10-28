package com.mingdy.coupon.infrastructure.cache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 정합성 점검 배치가 쓰는 Redis 접근. 발급 경로(IssuanceRedisRepository)와 같은 키를 읽고 보정한다.
//   coupon:{id}:stock     재고 카운터
//   coupon:{id}:users     발급된 사용자 set
//   coupon:{id}:sold_out  매진 플래그
//   coupon:reconcile:recent  최근 발급된 쿠폰 ZSET (member=couponId, score=발급 시각 ms)
@Repository
@RequiredArgsConstructor
public class CouponReconcileRedisRepository {

    private final StringRedisTemplate redisTemplate;

    // (from, to] 구간에 발급이 있었던 쿠폰 ID. rangeByScore 는 양끝 포함이라 from+1 로 하한을 배타로 만든다.
    public List<Long> couponIdsIssuedBetween(long fromExclusiveMs, long toInclusiveMs) {
        if (toInclusiveMs <= fromExclusiveMs) {
            return List.of();
        }
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(
                "coupon:reconcile:recent",
                (double) (fromExclusiveMs + 1),
                (double) toInclusiveMs
        );
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(CouponReconcileRedisRepository::toLongOrNull)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // 키가 없거나 숫자가 아니면 null → 호출 측에서 알람 처리.
    public Long stock(long couponId) {
        String raw = redisTemplate.opsForValue().get("coupon:" + couponId + ":stock");
        return raw == null ? null : toLongOrNull(raw);
    }

    public long userCount(long couponId) {
        Long size = redisTemplate.opsForSet().size("coupon:" + couponId + ":users");
        return size == null ? 0L : size;
    }

    public Set<String> userIds(long couponId) {
        Set<String> members = redisTemplate.opsForSet().members("coupon:" + couponId + ":users");
        return members == null ? Set.of() : members;
    }

    public boolean soldOutExists(long couponId) {
        return redisTemplate.hasKey("coupon:" + couponId + ":sold_out");
    }

    public void addUsers(long couponId, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().add(
                "coupon:" + couponId + ":users",
                userIds.stream().map(String::valueOf).toArray(String[]::new)
        );
    }

    public void deleteSoldOut(long couponId) {
        redisTemplate.delete("coupon:" + couponId + ":sold_out");
    }

    public void setSoldOut(long couponId, long ttlSeconds) {
        redisTemplate.opsForValue()
                .set("coupon:" + couponId + ":sold_out", "1", ttlSeconds, TimeUnit.SECONDS);
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