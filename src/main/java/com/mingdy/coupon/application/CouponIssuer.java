package com.mingdy.coupon.application;

import com.mingdy.coupon.support.AlreadyIssuedException;
import com.mingdy.coupon.support.SoldOutException;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CouponIssuer {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script = RedisScript.of(
            new ClassPathResource("lua/issue.lua"),
            Long.class
    );

    public CouponIssuer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 사용자 중복 검증 + 재고 검증 + 재고 차감 + 사용자 등록을 Lua 한 덩어리로 atomic 실행.
    // 실패 시 도메인 예외를 던진다.
    public void tryIssue(long couponId) {
        Long raw = redisTemplate.execute(
                script,
                List.of(stockKey(couponId), usersKey(couponId))
        );

        if(raw == null) throw new IllegalStateException("Lua 스크립트 결과가 null");

        if(raw == 1L) return;

        if(raw == 0L) throw new SoldOutException();

        if(raw == -1L) throw new AlreadyIssuedException();

        throw new IllegalStateException("예상치 못한 Lua 결과: " + raw);
    }

    public void initStock(long couponId, int totalQuantity) {
        redisTemplate.opsForValue().set(stockKey(couponId), String.valueOf(totalQuantity));
    }

    private String stockKey(long couponId) {
        return "coupon:" + couponId + ":stock";
    }

    private String usersKey(long couponId) {
        return "coupon:" + couponId + ":users";
    }
}
