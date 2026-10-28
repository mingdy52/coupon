package com.mingdy.coupon.infrastructure.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coupon.reconcile")
@RequiredArgsConstructor
@Getter
public class ReconcileProperties {

    private final long intervalMs;     // 배치 주기. 최근 발급 조회 구간의 너비 기준이기도 하다
    private final long gracePeriodMs;  // 방금 발급돼 DB 반영 중인 쿠폰을 빼기 위한 여유 시간

}
