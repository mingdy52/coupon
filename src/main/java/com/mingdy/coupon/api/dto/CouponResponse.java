package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.domain.Coupon;

import java.time.LocalDateTime;
import java.util.Objects;

public record CouponResponse(
        long id,
        String name,
        int totalQuantity,
        int validityDays,
        LocalDateTime startsAt
) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                Objects.requireNonNull(coupon.getId()),
                coupon.getName(),
                coupon.getTotalQuantity(),
                coupon.getValidityDays(),
                coupon.getStartsAt()
        );
    }

}
