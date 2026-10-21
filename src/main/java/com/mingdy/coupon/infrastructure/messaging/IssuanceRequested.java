package com.mingdy.coupon.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class IssuanceRequested {

    private final long couponId;
    private final long userId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;

}
