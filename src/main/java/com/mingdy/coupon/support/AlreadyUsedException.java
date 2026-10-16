package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class AlreadyUsedException extends DomainException {

    public AlreadyUsedException() {
        this("이미 사용된 쿠폰입니다");
    }

    public AlreadyUsedException(String message) {
        super("ALREADY_USED", HttpStatus.CONFLICT, message);
    }
}

