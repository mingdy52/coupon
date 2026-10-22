package com.mingdy.coupon.api.dto;

import com.mingdy.coupon.application.CacheMetricsSnapshot;

public record CacheMetricsResponse(
        long couponDbReads, // 쿠폰 정보를 DB에서 읽은 횟수
        long couponCacheHits, // 쿠폰 정보를 캐시에서 찾은 횟수
        long soldOutRedisExists, // Redis 매진 플래그를 확인한 횟수
        long soldOutFastPathHits // L1(Caffeine) 매진 캐시로 바로 거절한 횟수
) {

    public static CacheMetricsResponse from(CacheMetricsSnapshot snapshot) {
        return new CacheMetricsResponse(
                snapshot.getCouponDbReads(),
                snapshot.getCouponCacheHits(),
                snapshot.getSoldOutRedisExists(),
                snapshot.getSoldOutFastPathHits()
        );
    }

}
