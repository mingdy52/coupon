package com.mingdy.coupon.infrastructure.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coupon.cache")
@Getter
@RequiredArgsConstructor
public class CacheProperties {

    private final long ttlMs; // 쿠폰 정보 캐시 TTL
    private final long simulatedLoadLatencyMs; // 쿠폰 DB 조회에 더할 인위적 지연 (부하 측정용)

}
