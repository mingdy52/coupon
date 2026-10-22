package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.CacheMetricsResponse;
import com.mingdy.coupon.application.CacheMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 부하 측정용. 메모리에 쌓인 캐시 지표를 조회하고, 회차 사이에 0 으로 리셋한다.
@RestController
@RequestMapping("/metrics/cache")
@RequiredArgsConstructor
public class CacheMetricsController {

    private final CacheMetrics cacheMetrics;

    @GetMapping
    public CacheMetricsResponse snapshot() {
        return CacheMetricsResponse.from(cacheMetrics.snapshot());
    }

    @PostMapping("/reset")
    public void reset() {
        cacheMetrics.reset();
    }

}
