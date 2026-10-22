package com.mingdy.coupon.application;

import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.infrastructure.cache.CacheProperties;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequestProducer;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequested;
import com.mingdy.coupon.support.CouponNotFoundException;
import com.mingdy.coupon.support.NotStartedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CacheProperties cacheProperties;
    private final CacheMetrics cacheMetrics;
    private final CouponIssuer couponIssuer;
    private final IssuanceRequestProducer issuanceRequestProducer;

    @Transactional
    public Coupon createCoupon(CreateCouponRequest request) {
        Coupon coupon = couponRepository.save(
                new Coupon(
                        request.name(),
                        request.totalQuantity(),
                        request.validityDays(),
                        request.startsAt()
                )
        );

        couponIssuer.initStock(Objects.requireNonNull(coupon.getId()), coupon.getTotalQuantity());
        return coupon;
    }

    @Transactional
    public Issuance issue(long couponId, long userId) {
        cacheMetrics.incrementCouponDbRead();
        simulateLoadLatency();

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isBookingOpen(now)) {
            throw new NotStartedException();
        }

        couponIssuer.tryIssue(couponId, userId);

        LocalDateTime expiresAt = now.plusDays(coupon.getValidityDays());
        issuanceRequestProducer.publish(
                new IssuanceRequested(
                        couponId,
                        userId,
                        now,
                        expiresAt
                )
        );

        return new Issuance(
                userId,
                couponId,
                now,
                expiresAt
        );

    }

    // 부하 측정용. 쿠폰 조회를 느린 DB 조회처럼 만들어 캐시 유무의 차이를 드러낸다.
    // @Transactional 안이라 커넥션을 쥔 채 대기하므로 커넥션 풀이 병목이 된다.
    // 인터럽트되면 플래그를 복구하고 중단한다.
    private void simulateLoadLatency() {
        try {
            Thread.sleep(cacheProperties.getSimulatedLoadLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
