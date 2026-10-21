package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

public class QueueFullException extends DomainException {

    public QueueFullException() {
        this("발급 큐가 일시적으로 가득 찼습니다");
    }

    public QueueFullException(String message) {
        super("QUEUE_FULL", HttpStatus.SERVICE_UNAVAILABLE, message);
    }

}