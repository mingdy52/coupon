package com.mingdy.coupon.batch;

import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.infrastructure.cache.CouponReconcileRedisRepository;
import com.mingdy.coupon.infrastructure.cache.ReconcileProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 정합성 점검 배치의 진입점. "어떤 쿠폰을 점검할지" 고르고, 실제 점검은 CouponReconciler 에 맡긴다.
// - scheduledRecent: 주기마다 최근 발급이 있었던 쿠폰만 점검 (가벼움)
// - auditAll: 운영자가 수동으로 전체 쿠폰을 점검
@Component
@RequiredArgsConstructor
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final CouponRepository couponRepository;
    private final CouponReconcileRedisRepository reconcileRedisRepository;
    private final ReconcileProperties reconcileProperties;
    private final CouponReconciler couponReconciler;

    // interval-ms(기본 1분)마다 실행. coupon:reconcile:recent ZSET(score=발급 시각)에서
    // (from, cutoff] 구간에 발급된 쿠폰만 뽑아 점검한다.
    //   cutoff = now - grace : 방금 발급돼 Kafka → DB 저장이 아직 진행 중일 수 있는 쿠폰은 제외
    //   from   = cutoff - interval*2 : 주기 두 칸을 겹쳐 봐서 실행 지연이 있어도 빠지는 구간이 없게 함
    @Scheduled(fixedRateString = "${coupon.reconcile.interval-ms}")
    public void scheduledRecent() {
        long cutoffMs = System.currentTimeMillis() - reconcileProperties.getGracePeriodMs();
        long fromMs = cutoffMs - reconcileProperties.getIntervalMs() * 2; // 누락방지
        List<Long> couponIds = reconcileRedisRepository.couponIdsIssuedBetween(fromMs, cutoffMs);
        reconcileCoupons(couponIds);
    }

    // 수동으로 전수조사할때
    public ReconcileReport auditAll() {
        return reconcileCoupons(
                couponRepository.findAll().stream()
                        .map(Coupon::getId)
                        .filter(Objects::nonNull)
                        .toList()
        );
    }

    // 쿠폰별로 점검하고 결과를 합산. 한 쿠폰에서 예외가 나도 나머지는 계속 점검한다.
    private ReconcileReport reconcileCoupons(List<Long> couponIds) {
        List<CouponReconcileOutcome> outcomes = new ArrayList<>();
        for (Long couponId : couponIds) {
            try {
                outcomes.add(couponReconciler.reconcile(couponId));
            } catch (Exception e) {
                log.warn("reconcile 중 예외 coupon={}, 다음 회차에 재시도", couponId, e);
                outcomes.add(new CouponReconcileOutcome(0, 0L, true));
            }
        }
        return new ReconcileReport(
                outcomes.stream().mapToInt(CouponReconcileOutcome::autoFixed).sum(),
                (int) outcomes.stream().filter(CouponReconcileOutcome::driftAlert).count(),
                outcomes.stream().mapToLong(CouponReconcileOutcome::redisDbDrift).sum()
        );
    }
}