package com.mingdy.coupon.application;

import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.infrastructure.cache.CacheProperties;
import com.mingdy.coupon.infrastructure.cache.CouponCacheRepository;
import com.mingdy.coupon.support.CouponNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 쿠폰 발급 정책 조회. 캐시에 있으면 Redis에서, 없으면 DB에서 읽어 캐시에 채운다.
@Service
@RequiredArgsConstructor
public class CouponIssuePolicyReader {

    private final CouponRepository couponRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CacheProperties cacheProperties;

    public CouponIssuePolicy get(long id) {
        return couponCacheRepository.getIssuePolicyOrLoad(id, () -> {
            simulateLoadLatency();
            return CouponIssuePolicy.from(
                    couponRepository.findById(id).orElseThrow(CouponNotFoundException::new)
            );
        });
    }

    // 부하 측정용. 캐시 미스 때만 느린 DB 조회처럼 지연시켜 캐시 유무의 차이를 드러낸다.
    // issue() 의 @Transactional 안이라 커넥션을 쥔 채 대기한다.
    // 인터럽트되면 플래그를 복구하고 중단한다.
    private void simulateLoadLatency() {
        try {
            Thread.sleep(cacheProperties.getSimulatedLoadLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
