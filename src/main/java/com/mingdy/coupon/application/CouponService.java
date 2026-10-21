package com.mingdy.coupon.application;

import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequested;
import com.mingdy.coupon.support.CouponNotFoundException;
import com.mingdy.coupon.support.NotStartedException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssuer couponIssuer;
    // private final InMemoryIssuanceQueue inMemoryIssuanceQueue;
    private final ApplicationEventPublisher eventPublisher;


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
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isBookingOpen(now)) {
            throw new NotStartedException();
        }

        couponIssuer.tryIssue(couponId, userId);

        LocalDateTime expiresAt = now.plusDays(coupon.getValidityDays());
        eventPublisher.publishEvent(
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

}
