package com.mingdy.coupon.application;

import com.mingdy.coupon.api.dto.CreateCouponRequest;
import com.mingdy.coupon.domain.Coupon;
import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.domain.IssuanceRepository;
import com.mingdy.coupon.support.AlreadyIssuedException;
import com.mingdy.coupon.support.CouponNotFoundException;
import com.mingdy.coupon.support.NotStartedException;
import com.mingdy.coupon.support.SoldOutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuanceRepository issuanceRepository;
    private final CouponIssuer couponIssuer;

    @Transactional
    public Coupon createCoupon(CreateCouponRequest request) {
        Coupon coupon = couponRepository.save(
                new Coupon(
                        request.name(),
                        request.totalQuantity(),
                        request.validityDays(),
                        request.startsAt()
                )
        );

        couponIssuer.initStock(Objects.requireNonNull(coupon.getId()), coupon.getTotalQuantity());
        return coupon;
    }

    @Transactional
    public Issuance issue(long couponId, long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isBookingOpen(now)) {
            throw new NotStartedException();
        }

        if (coupon.isSoldOut()) {
            throw new SoldOutException();
        }

        if(issuanceRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new AlreadyIssuedException();
        }

        /*
        이전에는 비관적 락이 트랜잭션 전체를 임계 영역으로 잡았다. 기다리는 구간이 트랜잭션 전체라서 느리다.
          A: [락 획득 ── 조회 ── 중복확인 ── INSERT ── 커밋, 락 해제]
          B:                                                    [락 획득 ── ...]   ← A가 끝날 때까지 대기

        Redis는 명령을 한 번에 하나씩만 처리한다(싱글 스레드).
        Lua 스크립트도 통째로 하나의 명령처럼 실행되고, 도중에 다른 요청이 끼어들 수 없다.
            if 재고 > 0 then 재고 = 재고 - 1; return 성공 else return 매진 end
        줄을 서는 구간은 이 스크립트 한 번(마이크로초 단위)뿐이라 짧은 락처럼 동작한다.

        통과한 요청만 아래로 내려가서 DB에 발급하니, 재고보다 많이 통과할 수가 없다.
         */
        couponIssuer.tryIssue(couponId);

        /*
         Redis는 "몇 명 통과시킬지"만 확인한다. DB의 issued_quantity 숫자는 별개다.

         coupon은 락 없이 조회한 값
            Redis 재고 = 2,  DB issued_quantity = 4998
                A: DB 조회 → 4998
                B: DB 조회 → 4998
                A: Lua 통과 (재고 2 → 1)
                B: Lua 통과 (재고 1 → 0)
                A: 4998 + 1 → 4999 저장
                B: 4998 + 1 → 4999 저장   ← 2장 발급했는데 카운터는 1만 증가

            !!!! 카운터 유실(lost update) 발생 !!!!
            발급 건수 자체는 Redis가 막아서 맞지만, count_match는 FAIL이 나온다.

        coupon.incrementIssuedQuantity();
        */

        /*
        원자적 업데이트: 계산을 자바가 아니라 DB가 한다.
            UPDATE coupon SET issued_quantity = issued_quantity + 1 WHERE id = ?

            "내가 읽은 4998에 1을 더해라" (X)
            "지금 DB에 있는 값에 1을 더해라" (O)

            DB는 이 UPDATE 한 문장을 처리하는 동안만 행을 잠그고 A, B가 동시에 와도 4998 → 4999 → 5000으로 둘 다 반영된다.
        */
        couponRepository.incrementIssuedQuantity(couponId);

        /* 참고
            tryIssue로 Redis 재고를 먼저 줄이고 나서 DB에 저장한다.
            만약 재고 차감 후 실패하면(UNIQUE 제약 위반, DB 오류 등) DB는 롤백되는데 Redis 재고는 줄어든 채로 남는다.
            결과로 5,000장을 다 못 나눠주게 될 수 있다. (과소 발급)
            실패할 때 Redis 재고를 다시 늘려주는 보상 처리로 해결할 수 있다.
        */

        return issuanceRepository.save(
                new Issuance(
                        userId,
                        couponId,
                        now,
                        now.plusDays(coupon.getValidityDays())
                )
        );
    }

}
