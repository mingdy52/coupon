package com.mingdy.coupon.support;

import org.springframework.http.HttpStatus;

// 줄에도 없고 입장권도 없는 사용자가 상태를 조회했을 때 (404).
public class WaitingRoomNotEnteredException extends DomainException {

    public WaitingRoomNotEnteredException() {
        this("대기실에 먼저 진입해야 합니다");
    }

    public WaitingRoomNotEnteredException(String message) {
        super("WAITING_ROOM_NOT_ENTERED", HttpStatus.NOT_FOUND, message);
    }
}