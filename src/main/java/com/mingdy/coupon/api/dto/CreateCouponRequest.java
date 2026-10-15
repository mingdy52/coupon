package com.mingdy.coupon.api.dto;

import java.time.LocalDateTime;

public record CreateCouponRequest(
        String name,
        Integer totalQuantity,
        Integer validityDays,
        LocalDateTime startsAt
) {

    public CreateCouponRequest {
        if (totalQuantity == null) totalQuantity = 5000;
        if (validityDays == null) validityDays = 7;
    }

}
