package com.mingdy.coupon.batch;

// 한 번의 배치 실행 결과. 쿠폰별 CouponReconcileOutcome 을 합산한 값.
public record ReconcileReport(
        int autoFixed,     // 자동 보정 항목 수 합계
        int driftAlerts,   // 알람 난 쿠폰 수
        long redisDbDrift  // DB 측 불일치 수량 합계
) { }