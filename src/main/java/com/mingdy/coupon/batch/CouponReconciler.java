package com.mingdy.coupon.batch;

import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.IssuanceRepository;
import com.mingdy.coupon.infrastructure.cache.CouponReconcileRedisRepository;
import com.mingdy.coupon.infrastructure.cache.SoldOutProperties;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 쿠폰 한 건의 Redis ↔ DB 정합성을 점검하고, 안전한 것만 자동 보정한다.
// 기준 수식: totalQuantity == 발급 수 + Redis 재고
//   1) DB 기준  : total - (DB issuedQuantity + stock)  ≠ 0 → DB 쪽 문제. 자동 보정 불가, 알람만.
//   2) 매진 플래그: stock 과 sold_out 키가 어긋나면 자동 보정.
//   3) Redis 기준: total - (Redis users 수 + stock) > 0 → users set 누락. DB 에서 찾아 SADD 로 복구.
@Component
@RequiredArgsConstructor
public class CouponReconciler {

    private static final Logger log = LoggerFactory.getLogger(CouponReconciler.class);

    private final CouponRepository couponRepository;
    private final IssuanceRepository issuanceRepository;
    private final CouponReconcileRedisRepository reconcileRedisRepository;
    private final SoldOutProperties soldOutProperties;

    public CouponReconcileOutcome reconcile(long couponId) {
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return alert(couponId, "쿠폰 없음");
        }
        Long stockOrNull = reconcileRedisRepository.stock(couponId);
        if (stockOrNull == null) {
            return alert(couponId, "Redis stock 없음");
        }
        long stock = stockOrNull;

        // 1) DB 기준 점검. Redis 는 차감됐는데 DB 저장이 안 된 경우(DLT 로 빠진 메시지 등) 여기서 걸린다.
        //    어느 쪽이 맞는지 기계가 판단할 수 없으므로 Redis 는 건드리지 않고 알람만 남긴다.
        long dbDrift = coupon.getTotalQuantity() - (coupon.getIssuedQuantity() + stock);
        boolean stockNegative = stock < 0;
        if (stockNegative) {
            log.warn("재고 음수 감지 coupon={} stock={}", couponId, stock);
        }
        if (dbDrift != 0L) {
            log.warn("DB 측 불일치 감지 coupon={} residual={} (자동 보정 불가, 사람 확인)", couponId, dbDrift);
        }
        if (stockNegative || dbDrift != 0L) {
            return new CouponReconcileOutcome(0, Math.abs(dbDrift), true);
        }

        // 여기부터는 DB 수식이 맞는 상태 = DB 를 정답으로 믿고 Redis 를 맞춰도 안전하다.
        // 2) 매진 플래그 보정
        int autoFixed = reconcileSoldOut(couponId, stock);

        // 3) Redis users set 점검. 양수 = set 에서 사용자가 빠짐, 음수 = set 에 사용자가 더 많음.
        long userDrift = coupon.getTotalQuantity() - (reconcileRedisRepository.userCount(couponId) + stock);
        boolean driftAlert = false;
        if (userDrift > 0) {
            Set<Long> redisUserIds = reconcileRedisRepository.userIds(couponId).stream()
                    .map(CouponReconciler::toLongOrNull)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<Long> missingUserIds = issuanceRepository.findUserIdsByCouponId(couponId).stream()
                    .filter(userId -> !redisUserIds.contains(userId))
                    .toList();
            // DB 에는 있고 Redis set 에는 없는 사용자 = 누락분.
            // 누락 수가 차이와 정확히 일치할 때만 복구한다. 안 맞으면 원인이 다른 것이라 알람으로 넘긴다.
            if (missingUserIds.size() == userDrift) {
                reconcileRedisRepository.addUsers(couponId, missingUserIds);
                autoFixed++;
                log.info("auto-fix: 사용자 목록 SADD {}건 coupon={}", missingUserIds.size(), couponId);
            } else {
                driftAlert = true;
                log.warn("Redis 사용자 목록을 안전하게 복구할 수 없음 coupon={}", couponId);
            }
        } else if (userDrift < 0) {
            // set 이 더 많은 경우는 어떤 사용자를 빼야 할지 확정할 수 없어 알람만.
            driftAlert = true;
            log.warn("Redis 사용자 목록 초과 감지 coupon={} residual={}", couponId, userDrift);
        }

        return new CouponReconcileOutcome(autoFixed, 0L, driftAlert);
    }

    // 재고와 매진 플래그를 일치시킨다. 보정했으면 1, 아니면 0.
    //   재고 남았는데 플래그 있음 → 발급이 막혀 있으니 플래그 삭제
    //   재고 0 인데 플래그 없음   → 매진 fast-path 가 안 먹으니 플래그 설정
    private int reconcileSoldOut(long couponId, long stock) {
        boolean soldOut = reconcileRedisRepository.soldOutExists(couponId);
        if (stock > 0 && soldOut) {
            reconcileRedisRepository.deleteSoldOut(couponId);
            log.info("auto-fix: 매진 플래그 해제 coupon={}", couponId);
            return 1;
        }
        if (stock == 0L && !soldOut) {
            reconcileRedisRepository.setSoldOut(couponId, soldOutProperties.ttlSeconds());
            log.info("auto-fix: 매진 플래그 설정 coupon={}", couponId);
            return 1;
        }
        return 0;
    }

    // 점검 자체가 불가능한 경우 (쿠폰/재고 키 없음). 알람으로 기록하고 넘어간다.
    private CouponReconcileOutcome alert(long couponId, String reason) {
        log.warn("대상 확인 불가 coupon={} reason={} (수동 확인 필요)", couponId, reason);
        return new CouponReconcileOutcome(0, 0L, true);
    }

    // 숫자가 아니면 null. 형식이 깨진 값을 걸러내는 용도.
    private static Long toLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
