package com.mingdy.coupon.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

// 부하 측정용. 단일 인스턴스 비교라 Prometheus 대신 AtomicLong 으로 간단히.
@Component
public class CacheMetrics {

    private final AtomicLong couponDbReads = new AtomicLong();
    private final AtomicLong couponCacheHits = new AtomicLong();
    private final AtomicLong soldOutRedisExists = new AtomicLong();
    private final AtomicLong soldOutFastPathHits = new AtomicLong();

    public void incrementCouponDbRead() {
        couponDbReads.incrementAndGet();
    }

    public void incrementCouponCacheHit() {
        couponCacheHits.incrementAndGet();
    }

    public void incrementSoldOutRedisExists() {
        soldOutRedisExists.incrementAndGet();
    }

    public void incrementSoldOutFastPathHit() {
        soldOutFastPathHits.incrementAndGet();
    }

    public CacheMetricsSnapshot snapshot() {
        return new CacheMetricsSnapshot(
                couponDbReads.get(),
                couponCacheHits.get(),
                soldOutRedisExists.get(),
                soldOutFastPathHits.get()
        );
    }

    public void reset() {
        couponDbReads.set(0);
        couponCacheHits.set(0);
        soldOutRedisExists.set(0);
        soldOutFastPathHits.set(0);
    }

}
