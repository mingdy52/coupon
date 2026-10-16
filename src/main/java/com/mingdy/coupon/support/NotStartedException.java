package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class NotStartedException extends DomainException {

    public NotStartedException() {
        this("발급이 아직 시작되지 않았습니다");
    }

    public NotStartedException(String message) {
        super("NOT_STARTED", HttpStatus.CONFLICT, message);
    }
}
