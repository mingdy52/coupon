package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.application.Admission;

// 진입과 상태 조회의 공통 응답. 통과하면 position, estimatedWaitSeconds 는 0.
public record WaitingRoomResponse (
        boolean admitted,
        long position,
        long estimatedWaitSeconds
) {

    public static WaitingRoomResponse from(Admission admission) {
        return new WaitingRoomResponse(
                admission.admitted(),
                admission.position(),
                admission.estimatedWaitSeconds()
        );
    }

}
