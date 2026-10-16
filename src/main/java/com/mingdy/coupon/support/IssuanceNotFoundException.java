package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class IssuanceNotFoundException extends DomainException {

    public IssuanceNotFoundException() {
        this("발급 내역을 찾을 수 없습니다");
    }

    public IssuanceNotFoundException(String message) {
        super("ISSUANCE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
