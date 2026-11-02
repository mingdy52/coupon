package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

// 입장권 없이 발급 API 를 호출했을 때 (403).
public class NoWaitingRoomPassException extends DomainException {

    public NoWaitingRoomPassException() {
        this("대기실 입장권이 없습니다 (먼저 대기실을 통과하세요)");
    }

    public NoWaitingRoomPassException(String message) {
        super("NO_WAITING_ROOM_PASS", HttpStatus.FORBIDDEN, message);
    }
}