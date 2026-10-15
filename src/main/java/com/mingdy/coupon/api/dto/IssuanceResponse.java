package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.domain.IssuanceStatus;

import java.time.LocalDateTime;
import java.util.Objects;

public record IssuanceResponse(
        long id,
        long userId,
        long couponId,
        IssuanceStatus status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt
) {

    public static IssuanceResponse from(Issuance issuance) {
        return new IssuanceResponse(
                Objects.requireNonNull(issuance.getId()),
                issuance.getUserId(),
                issuance.getCouponId(),
                issuance.getStatus(),
                issuance.getIssuedAt(),
                issuance.getExpiresAt(),
                issuance.getUsedAt()
        );
    }

}
