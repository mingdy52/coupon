package com.mingdy.coupon.application;

import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.domain.IssuanceRepository;
import com.mingdy.coupon.support.AlreadyIssuedException;
import com.mingdy.coupon.support.CouponNotFoundException;
import com.mingdy.coupon.support.NotStartedException;
import com.mingdy.coupon.support.SoldOutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuanceRepository issuanceRepository;

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

        if (coupon.isSoldOut()) {
            throw new SoldOutException();
        }

        if(issuanceRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new AlreadyIssuedException();
        }

        coupon.incrementIssuedQuantity();

        return issuanceRepository.save(
                new Issuance(
                        userId,
                        couponId,
                        now,
                        now.plusDays(coupon.getValidityDays())
                )
        );
    }

}
