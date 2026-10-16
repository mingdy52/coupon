package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class CouponNotFoundException extends DomainException {

    public CouponNotFoundException() {
        this("쿠폰 행사를 찾을 수 없습니다");
    }

    public CouponNotFoundException(String message) {
        super("COUPON_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
