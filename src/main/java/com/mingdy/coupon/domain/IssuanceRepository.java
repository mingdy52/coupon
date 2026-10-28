package com.mingdy.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssuanceRepository extends JpaRepository<Issuance, Long> {

    boolean existsByUserIdAndCouponId(long userId, long couponId);

    List<Issuance> findByUserIdOrderByIssuedAtDesc(long userId);

    // reconcile 에서 Redis users set 누락분을 찾기 위한 DB 기준 발급자 목록.
    @Query("SELECT i.userId FROM Issuance i WHERE i.couponId = :couponId")
    List<Long> findUserIdsByCouponId(@Param("couponId") Long couponId);

}
