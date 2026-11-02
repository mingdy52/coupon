package com.mingdy.coupon.application;

public interface WaitingRoom {

    // 대기열에 등록하고 현재 상태를 돌려준다. 이미 줄에 있거나 통과했으면 그대로 유지한다.
    Admission enter(long couponId, long userId);

    // 등록 없이 상태만 조회한다. 줄에도 없고 입장권도 없으면 예외.
    Admission status(long couponId, long userId);

    // 발급 API 에서 쓰는 입장권 확인.
    boolean isAdmitted(long couponId, long userId);

}
