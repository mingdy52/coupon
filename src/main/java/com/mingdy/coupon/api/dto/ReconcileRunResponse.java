package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.batch.ReconcileReport;

// POST /admin/reconcile/run 응답.
public record ReconcileRunResponse(
        int autoFixed,     // 자동 보정한 건수 (매진 플래그 수정, 누락 사용자 복구 등)
        int driftAlerts,   // 자동 보정 못 해 사람이 확인해야 하는 쿠폰 수
        long redisDbDrift  // DB 측 불일치 수량 합계 (Redis 는 차감됐는데 DB 저장이 안 된 건 등)
) {

    public static ReconcileRunResponse from(ReconcileReport report) {
        return new ReconcileRunResponse(
                report.autoFixed(),
                report.driftAlerts(),
                report.redisDbDrift()
        );
    }
}