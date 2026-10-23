package com.mingdy.coupon.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coupon.sold-out")
public record SoldOutProperties(
        long ttlSeconds, // Redis 매진 플래그 TTL. 이벤트가 끝날 때까지 유지되도록 길게 둔다
        long fastPathTtlMs // L1(Caffeine) 매진 여부 캐시 TTL. 매진 직후 이 시간만큼은 매진을 모를 수 있다
) {
}
