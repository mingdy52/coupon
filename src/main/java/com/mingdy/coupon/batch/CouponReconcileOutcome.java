package com.mingdy.coupon.batch;

// 쿠폰 한 건을 점검한 결과.
public record CouponReconcileOutcome(
        int autoFixed,      // 이 쿠폰에서 자동 보정한 항목 수
        long redisDbDrift,  // 재고 수식이 안 맞는 수량 (|total - (DB 발급 수 + Redis 재고)|)
        boolean driftAlert  // 자동 보정 불가 → 사람 확인 필요
) { }