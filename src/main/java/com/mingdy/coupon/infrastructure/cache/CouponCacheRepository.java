package com.mingdy.coupon.infrastructure.cache;

import com.mingdy.coupon.application.CacheMetrics;
import com.mingdy.coupon.application.CouponIssuePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.function.Supplier;

@Repository
@RequiredArgsConstructor
public class CouponCacheRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final CacheMetrics cacheMetrics;

    public CouponIssuePolicy getIssuePolicyOrLoad(long id, Supplier<CouponIssuePolicy> loader) {
        return getOrLoad("coupon:" + id + ":issue-policy", loader);
    }

    // cache-aside. 미스면 loader 로 읽어 TTL 을 걸고 저장한다.
    // 동시 미스를 막지 않으므로 TTL 만료 직후 요청이 몰리면 전부 DB로 간다 (stampede).
    private CouponIssuePolicy getOrLoad(String key, Supplier<CouponIssuePolicy> loader) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            cacheMetrics.incrementCouponCacheHit();
            return objectMapper.readValue(cached, CouponIssuePolicy.class);
        }
        cacheMetrics.incrementCouponDbRead();
        CouponIssuePolicy response = loader.get();
        redisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(response),
                Duration.ofMillis(cacheProperties.getTtlMs())
        );
        return response;
    }
}
