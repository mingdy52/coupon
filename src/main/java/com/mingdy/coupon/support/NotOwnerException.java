package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class NotOwnerException extends DomainException {

    public NotOwnerException() {
        this("본인의 쿠폰이 아닙니다");
    }

    public NotOwnerException(String message) {
        super("NOT_OWNER", HttpStatus.FORBIDDEN, message);
    }
}