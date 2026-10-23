package com.mingdy.coupon.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// Redis 매진 플래그 조회. 플래그는 issue.lua 가 마지막 재고를 차감할 때 세팅한다.
@Repository
@RequiredArgsConstructor
public class SoldOutRedisRepository {

    private final StringRedisTemplate redisTemplate;

    // 키가 있으면 매진. 값은 의미 없고 키 존재 여부만 본다.
    public boolean isFlagged(long couponId) {
        return redisTemplate.hasKey("coupon:" + couponId + ":sold_out");
    }

}
