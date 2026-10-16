package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class SoldOutException extends DomainException{

    public SoldOutException() {
        this("쿠폰이 매진되었습니다");
    }

    public SoldOutException(String message) {
        super("SOLD_OUT", HttpStatus.CONFLICT, message);
    }

}
