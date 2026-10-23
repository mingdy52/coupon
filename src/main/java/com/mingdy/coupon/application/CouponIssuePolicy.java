package com.mingdy.coupon.application;

import com.mingdy.coupon.domain.Coupon;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

// 발급 판단에 필요한 쿠폰 정보만 담은 캐시용 값.
// 엔티티 대신 이 객체를 Redis에 JSON으로 저장해 재고·이름 같은 다른 필드와 분리한다.
@RequiredArgsConstructor
@Getter
public class CouponIssuePolicy {

    private final LocalDateTime startsAt;
    private final int validityDays;

    public static CouponIssuePolicy from(Coupon coupon) {
        return new CouponIssuePolicy(
                coupon.getStartsAt(),
                coupon.getValidityDays()
        );
    }

    public boolean isBookingOpen(LocalDateTime now) {
        return startsAt == null || !now.isBefore(startsAt);
    }

}
