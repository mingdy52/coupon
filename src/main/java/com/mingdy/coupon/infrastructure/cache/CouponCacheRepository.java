package com.mingdy.coupon.infrastructure.cache;

import com.mingdy.coupon.application.CacheMetrics;
import com.mingdy.coupon.application.CouponIssuePolicy;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.mingdy.coupon.infrastructure.cache.RedisLuaSupport.*;

@Repository
@RequiredArgsConstructor
public class CouponCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(CouponCacheRepository.class);

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

    // SWR 백그라운드 갱신용 스레드 풀. 요청 스레드는 stale 값으로 바로 응답하고 DB 조회는 여기서 한다.
    // 락 덕분에 키 하나당 갱신은 동시에 1건이라 스레드 4개면 충분하다.
    // daemon 스레드라 JVM 종료를 막지 않는다.
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "coupon-cache-swr-refresh");
        thread.setDaemon(true);
        return thread;
    });

    @SuppressWarnings("rawtypes")
    // 캐시 조회 + 신선도 판단 + 락 획득을 한 번에 하는 스크립트 (single flight + SWR)
    private final RedisScript<List> lookupScript = listLuaScript("lua/cache-single-flight-swr.lua");
    // private final RedisScript<List> singleFlightScript = listLuaScript("lua/cache-single-flight.lua");
    private final RedisScript<Long> releaseLockScript = longLuaScript("lua/release-lock.lua");


    public CouponIssuePolicy getIssuePolicyOrLoad(long id, Supplier<CouponIssuePolicy> loader) {
        return getOrLoad("coupon:" + id + ":issue-policy", "coupon:" + id + ":issue-policy:lock", loader);
    }

    // SWR (stale-while-revalidate) + single flight.
    // 캐시 값마다 저장 시각(fetchedAtMs)을 두고, 나이에 따라 두 단계로 나눈다.
    //   fresh (나이 < freshMs)        : 그대로 응답
    //   stale (freshMs <= 나이 < ttl) : 오래된 값으로 먼저 응답하고, 백그라운드에서 DB 를 다시 읽어 교체
    //   없음  (ttl 초과로 키 만료)     : 응답할 값이 없으므로 single flight 로 한 요청만 DB 조회
    // 부하가 계속 들어오면 stale 구간에서 갱신되므로 하드 TTL 만료(대기)가 거의 생기지 않는다.
    //
    // Lua 스크립트 결과
    //   HIT           : fresh 값, 또는 stale 이지만 다른 요청이 이미 갱신 중 → 바로 반환
    //   STALE_REFRESH : stale 이고 갱신 락을 잡음 → 값은 바로 반환, 갱신은 백그라운드로
    //   LOAD          : 값이 없고 락을 잡음 → 이 요청이 DB 조회 후 캐시를 채움
    //   WAIT          : 값이 없고 다른 요청이 채우는 중 → 잠깐 쉬고 다시 확인
    private CouponIssuePolicy getOrLoad(
            String cacheKey,
            String lockKey,
            Supplier<CouponIssuePolicy> loader
    ) {
        // 락 값으로 넣는 요청별 고유 토큰. 해제할 때 내가 잡은 락인지 확인하는 데 쓴다.
        String token = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // 신선도 판단 기준 시각. fetchedAtMs 와 같은 앱 시계를 쓴다.
            long now = Instant.now().toEpochMilli();
            List<String> result = runForStrings(
                    redisTemplate,
                    lookupScript,
                    List.of(cacheKey, lockKey),
                    now,
                    cacheProperties.getFreshMs(),
                    token, LOCK_TTL_MS
            );

            switch (result.get(0)) {
                case "HIT" -> {
                    cacheMetrics.incrementCouponCacheHit();
                    return objectMapper.readValue(result.get(1), CouponIssuePolicy.class);
                }
                case "STALE_REFRESH" -> {
                    // 응답은 stale 값으로 끝나므로 적중으로 센다. DB 조회는 fillCache 에서 따로 센다.
                    cacheMetrics.incrementCouponCacheHit();
                    // 락은 이 요청의 token 으로 잡혔으므로 백그라운드 작업에도 같은 token 을 넘겨 해제한다.
                    // 갱신이 실패해도 요청은 이미 응답했으니 로그만 남긴다. 락은 fillCache 에서 풀린다.
                    backgroundExecutor.execute(() -> {
                        try {
                            fillCache(cacheKey, lockKey, token, loader);
                        } catch (Exception e) {
                            log.warn("백그라운드 SWR 갱신 실패 (key={}): {}", cacheKey, e.getMessage());
                        }
                    });
                    return objectMapper.readValue(result.get(1), CouponIssuePolicy.class);
                }
                case "LOAD" -> {
                    return fillCache(cacheKey, lockKey, token, loader);
                }
                case "WAIT" -> waitBackoff();
                // 스크립트는 위 네 값만 반환한다.
                default -> {
                }
            }
        }

        // 로더가 오래 걸리거나 죽어서 재시도 안에 캐시가 안 채워진 경우
        throw new IllegalStateException("쿠폰 캐시 채우기 timeout (key=" + cacheKey + ")");
    }

    // DB 에서 읽어 캐시를 채운다. LOAD 는 요청 스레드에서, STALE_REFRESH 는 백그라운드에서 호출한다.
    // 값과 저장 시각을 Hash 한 키에 함께 저장해 Lua 스크립트가 신선도를 판단할 수 있게 한다.
    // TTL(ttlMs)은 하드 만료. 이 시간 동안 갱신이 없으면 키가 사라져 LOAD 로 돌아간다.
    private CouponIssuePolicy fillCache(
            String cacheKey,
            String lockKey,
            String token,
            Supplier<CouponIssuePolicy> loader
    ) {
        try {
            cacheMetrics.incrementCouponDbRead();
            CouponIssuePolicy response = loader.get();
            redisTemplate.<String, String>opsForHash().putAll(
                    cacheKey,
                    Map.of(
                            "value", objectMapper.writeValueAsString(response),
                            "fetchedAtMs", String.valueOf(Instant.now().toEpochMilli())
                    )
            );
            redisTemplate.expire(cacheKey, Duration.ofMillis(cacheProperties.getTtlMs()));
            return response;
        } finally {
            // 로드가 실패해도 락은 푼다. 그냥 DEL 하면 내 락이 TTL 로 만료된 뒤
            // 다른 요청이 새로 잡은 락까지 지울 수 있어, 토큰이 같을 때만 지운다.
            runForLong(redisTemplate, releaseLockScript, List.of(lockKey), token);
        }
    }

    // 애플리케이션 종료 시 스레드 풀을 정리한다. 새 갱신 작업은 받지 않고, 실행 중인 작업은 마저 끝낸다.
    @PreDestroy
    public void shutdown() {
        backgroundExecutor.shutdown();
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
