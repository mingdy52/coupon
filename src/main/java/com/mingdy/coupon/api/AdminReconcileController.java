package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.ReconcileRunResponse;
import com.mingdy.coupon.batch.Reconciler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 운영자가 정합성 점검(reconcile)을 즉시 돌리는 API. 스케줄 주기를 기다리지 않고 전체 쿠폰을 전수 점검한다.
@RestController
@RequestMapping("/admin/reconcile")
@RequiredArgsConstructor
public class AdminReconcileController {

    private final Reconciler reconciler;

    // 전수 점검 실행 후 결과(자동 보정 건수, 알람 건수, DB 측 불일치 수량)를 바로 돌려준다.
    @PostMapping("/run")
    public ReconcileRunResponse run() {
        return ReconcileRunResponse.from(reconciler.auditAll());
    }
}