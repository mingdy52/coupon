package com.mingdy.coupon.infrastructure.cache;

import static com.mingdy.coupon.infrastructure.cache.RedisLuaSupport.longLuaScript;
import static com.mingdy.coupon.infrastructure.cache.RedisLuaSupport.runForLong;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

// 발급 관련 Redis 접근 (재고 초기화, 발급 Lua 실행).
@Repository
@RequiredArgsConstructor
public class IssuanceRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final SoldOutProperties soldOutProperties;

    private final RedisScript<Long> issueScript = longLuaScript("lua/issue.lua");

    // 반환: 1=성공, 0=매진, -1=중복 발급
    // KEYS: 재고 카운터, 발급 사용자 set, 매진 플래그, 최근 발급 쿠폰 ZSET(reconcile 대상)
    // ARGV: 사용자 ID, 매진 플래그 TTL(초), 발급 시각(ms, ZSET score), 쿠폰 ID(ZSET member)
    public long tryIssue(long couponId, long userId) {
        return runForLong(
                redisTemplate,
                issueScript,
                List.of(
                        stockKey(couponId),
                        "coupon:" + couponId + ":users",
                        "coupon:" + couponId + ":sold_out",
                        "coupon:reconcile:recent"
                ),
                userId,
                soldOutProperties.ttlSeconds(),
                System.currentTimeMillis(),
                couponId
        );
    }

    public void initStock(long couponId, int totalQuantity) {
        redisTemplate.opsForValue().set(stockKey(couponId), String.valueOf(totalQuantity));
    }

    private String stockKey(long couponId) {
        return "coupon:" + couponId + ":stock";
    }
}
