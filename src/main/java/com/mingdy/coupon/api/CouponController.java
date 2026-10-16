package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.CouponResponse;
import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.api.dto.IssuanceResponse;
import com.mingdy.coupon.application.CouponService;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.Issuance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<CouponResponse> create(@RequestBody CreateCouponRequest request) {
        Coupon coupon = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CouponResponse.from(coupon));
    }

    @PostMapping("/{couponId}/issue")
    public IssuanceResponse issue(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        Issuance issuance = couponService.issue(couponId, userId);
        return IssuanceResponse.from(issuance);
    }

}
