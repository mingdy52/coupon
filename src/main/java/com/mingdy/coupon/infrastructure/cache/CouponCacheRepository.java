package com.mingdy.coupon.infrastructure.cache;

import com.mingdy.coupon.application.CacheMetrics;
import com.mingdy.coupon.application.CouponIssuePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.mingdy.coupon.infrastructure.cache.RedisLuaSupport.*;

@Repository
@RequiredArgsConstructor
public class CouponCacheRepository {

    // 대기 요청은 최대 50회 × 20ms = 약 1초 동안 캐시가 채워지길 기다리고, 그래도 안 되면 예외로 끝낸다.
    private static final int MAX_RETRIES = 50;
    // 락을 못 잡은 요청이 캐시를 다시 확인하기 전까지 쉬는 시간
    private static final long WAIT_BACKOFF_MS = 20L;
    // 로더가 락을 쥔 채 죽어도 이 시간이 지나면 락이 풀려 다른 요청이 로드할 수 있다.
    // DB 조회 시간(50ms)보다 충분히 길게 둔다.
    private static final long LOCK_TTL_MS = 3_000L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final CacheMetrics cacheMetrics;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> singleFlightScript = listLuaScript("lua/cache-single-flight.lua");
    private final RedisScript<Long> releaseLockScript = longLuaScript("lua/release-lock.lua");

    public CouponIssuePolicy getIssuePolicyOrLoad(long id, Supplier<CouponIssuePolicy> loader) {
        return getOrLoad("coupon:" + id + ":issue-policy", "coupon:" + id + ":issue-policy:lock", loader);
    }

    // single flight. 캐시 미스가 동시에 나도 락을 잡은 요청 하나만 DB를 조회한다 (stampede 방지).
    // 캐시 조회와 락 획득을 Lua 스크립트 하나로 묶어 원자적으로 판단한다.
    //   HIT  : 캐시에 값이 있음 → 바로 반환
    //   LOAD : 캐시가 비었고 락을 잡음 → DB 조회 후 캐시에 쓰고 락 해제
    //   WAIT : 캐시가 비었고 다른 요청이 로드 중 → 잠깐 쉬고 다시 확인
    private CouponIssuePolicy getOrLoad(
            String cacheKey,
            String lockKey,
            Supplier<CouponIssuePolicy> loader
    ) {
        // 락 값으로 넣는 요청별 고유 토큰. 해제할 때 내가 잡은 락인지 확인하는 데 쓴다.
        String token = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            List<String> result = runForStrings(
                    redisTemplate,
                    singleFlightScript,
                    List.of(cacheKey, lockKey),
                    token,
                    LOCK_TTL_MS
            );

            switch (result.get(0)) {
                case "HIT" -> {
                    cacheMetrics.incrementCouponCacheHit();
                    return objectMapper.readValue(result.get(1), CouponIssuePolicy.class);
                }
                case "LOAD" -> {
                    try {
                        cacheMetrics.incrementCouponDbRead();
                        CouponIssuePolicy response = loader.get();
                        String json = objectMapper.writeValueAsString(response);
                        redisTemplate.opsForValue()
                                .set(cacheKey, json, Duration.ofMillis(cacheProperties.getTtlMs()));
                        return response;
                    } finally {
                        // 로드가 실패해도 락은 푼다. 그냥 DEL 하면 내 락이 TTL 로 만료된 뒤
                        // 다른 요청이 새로 잡은 락까지 지울 수 있어, 토큰이 같을 때만 지운다.
                        runForLong(redisTemplate, releaseLockScript, List.of(lockKey), token);
                    }
                }
                case "WAIT" -> waitBackoff();
                // 스크립트는 위 세 값만 반환한다.
                default -> {
                }
            }
        }

        // 로더가 오래 걸리거나 죽어서 재시도 안에 캐시가 안 채워진 경우
        throw new IllegalStateException("쿠폰 캐시 채우기 timeout (key=" + cacheKey + ")");
    }

    private void waitBackoff() {
        try {
            Thread.sleep(WAIT_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
