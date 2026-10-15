package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.domain.Coupon;

import java.time.LocalDateTime;
import java.util.Objects;

public record CouponResponse(
        long id,
        String name,
        int totalQuantity,
        int issuedQuantity,
        int validityDays,
        LocalDateTime startsAt,
        LocalDateTime createdAt
) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                Objects.requireNonNull(coupon.getId()),
                coupon.getName(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getValidityDays(),
                coupon.getStartsAt(),
                coupon.getCreatedAt()
        );
    }

}
