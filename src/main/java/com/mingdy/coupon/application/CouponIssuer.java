package com.mingdy.coupon.application;

import com.mingdy.coupon.infrastructure.cache.IssuanceRedisRepository;
import com.mingdy.coupon.support.AlreadyIssuedException;
import com.mingdy.coupon.support.SoldOutException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponIssuer {

    private final IssuanceRedisRepository issuanceRedisRepository;
    private final RedisScript<Long> script = RedisScript.of(
            new ClassPathResource("lua/issue.lua"),
            Long.class
    );


    // 사용자 중복 검증 + 재고 검증 + 재고 차감 + 사용자 등록을 Lua 한 덩어리로 atomic 실행.
    // 마지막 재고를 차감한 요청은 Redis 매진 플래그도 함께 세팅한다.
    // Lua 결과 코드를 도메인 예외로 바꾼다.
    public void tryIssue(long couponId, long userId) {
        long result = issuanceRedisRepository.tryIssue(couponId, userId);

        if (result == 1L) return;
        if (result == 0L) throw new SoldOutException();
        if (result == -1L) throw new AlreadyIssuedException();

        throw new IllegalStateException("예상치 못한 Lua 결과");
    }

    public void initStock(long couponId, int totalQuantity) {
        issuanceRedisRepository.initStock(couponId, totalQuantity);
    }

}
