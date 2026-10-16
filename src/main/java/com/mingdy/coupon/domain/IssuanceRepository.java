package com.mingdy.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuanceRepository extends JpaRepository<Issuance, Long> {

    boolean existsByUserIdAndCouponId(long userId, long couponId);

    List<Issuance> findByUserIdOrderByIssuedAtDesc(long userId);
}
