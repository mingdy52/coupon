package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.domain.IssuanceDltLog;

import java.time.LocalDateTime;
import java.util.Objects;

public record IssuanceDltResponse(long id, String messageKey, String payload, String errorMessage, String status,
                                  LocalDateTime receivedAt) {

    public static IssuanceDltResponse from(IssuanceDltLog log) {
        return new IssuanceDltResponse(
                Objects.requireNonNull(log.getId()),
                log.getMessageKey(),
                log.getPayload(),
                log.getErrorMessage(),
                log.getStatus().name(),
                log.getReceivedAt()
        );
    }
}
