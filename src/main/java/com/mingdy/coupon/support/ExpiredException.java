package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class ExpiredException extends DomainException {

    public ExpiredException() {
        this("유효기간이 만료된 쿠폰입니다");
    }

    public ExpiredException(String message) {
        super("EXPIRED", HttpStatus.CONFLICT, message);
    }
}