package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class AlreadyIssuedException extends DomainException {

    public AlreadyIssuedException() {
        this("이미 발급된 쿠폰입니다");
    }

    public AlreadyIssuedException(String message) {
        super("ALREADY_ISSUED", HttpStatus.CONFLICT, message);
    }

}
