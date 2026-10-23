package com.mingdy.coupon.application;

import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequestProducer;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequested;
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
    private final CouponIssuePolicyReader couponIssuePolicyReader;
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
        // 쿠폰 정보는 캐시에서 읽는다. 캐시 미스일 때만 DB를 조회한다.
        CouponIssuePolicy policy = couponIssuePolicyReader.get(couponId);

        LocalDateTime now = LocalDateTime.now();
        if (!policy.isBookingOpen(now)) {
            throw new NotStartedException();
        }

        couponIssuer.tryIssue(couponId, userId);

        LocalDateTime expiresAt = now.plusDays(policy.getValidityDays());
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

}
