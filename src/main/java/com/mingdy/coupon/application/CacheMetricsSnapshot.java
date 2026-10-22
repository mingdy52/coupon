package com.mingdy.coupon.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CacheMetricsSnapshot {

    private final long couponDbReads;
    private final long couponCacheHits;
    private final long soldOutRedisExists;
    private final long soldOutFastPathHits;

}
