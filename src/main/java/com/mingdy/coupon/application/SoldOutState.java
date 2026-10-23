package com.mingdy.coupon.application;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.mingdy.coupon.infrastructure.cache.SoldOutProperties;
import com.mingdy.coupon.infrastructure.cache.SoldOutRedisRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 쿠폰별 매진 여부를 앱 메모리(Caffeine, L1)에 캐싱한다.
// 매진 이후 몰리는 요청을 Redis 까지 보내지 않고 앱에서 바로 거절하기 위한 fast path.
//
// 흐름 (쿠폰 하나 기준)
//   1. 처음 묻거나 L1 값이 만료됨 → Redis 에 매진 플래그(coupon:{id}:sold_out)가 있는지 확인하고 결과를 L1 에 저장
//   2. fast-path-ttl-ms(1초) 동안은 Redis 를 보지 않고 L1 값으로 답한다
//   3. 1초가 지나면 L1 값이 사라져 다시 1번으로
// 매진 아님(false)도 1초간 캐싱되므로, 매진 직후 최대 1초 동안은 매진을 모른 채 요청이 Redis 로 간다.
// 그래도 issue.lua 가 재고를 원자적으로 확인하므로 초과 발급은 생기지 않는다 (정합성은 Lua 가 책임).
@Component
public class SoldOutState {

    // L1 에 담아둘 쿠폰 수 상한. 넘으면 오래 안 쓴 쿠폰부터 지워 메모리가 무한히 늘지 않게 한다.
    private static final long MAX_TRACKED_COUPONS = 1_000L;

    private final CacheMetrics cacheMetrics;
    private final LoadingCache<Long, Boolean> soldOutCache;

    public SoldOutState(
            SoldOutRedisRepository soldOutRedisRepository,
            CacheMetrics cacheMetrics,
            SoldOutProperties soldOutProperties
    ) {
        this.cacheMetrics = cacheMetrics;
        this.soldOutCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(soldOutProperties.fastPathTtlMs()))
                .maximumSize(MAX_TRACKED_COUPONS)
                // L1 에 값이 없을 때만 실행되는 로더. Redis 매진 플래그 키가 있으면 true(매진), 없으면 false.
                // 이 로더가 실행된 횟수 = Redis 까지 간 횟수라 soldOutRedisExists 로 센다.
                .build(couponId -> {
                    cacheMetrics.incrementSoldOutRedisExists();
                    return soldOutRedisRepository.isFlagged(couponId);
                });
    }

    // L1 에 있으면 그 값을, 없으면 로더로 Redis 를 확인해 채운 값을 돌려준다.
    // 매진으로 판단해 거절한 횟수를 soldOutFastPathHits 로 센다.
    public boolean isSoldOut(long couponId) {
        // 로더가 null 을 반환하는 경우는 없지만 LoadingCache.get 은 nullable 이라 방어한다.
        Boolean cached = soldOutCache.get(couponId);
        boolean soldOut = cached != null && cached;
        if (soldOut) {
            cacheMetrics.incrementSoldOutFastPathHit();
        }
        return soldOut;
    }

}
