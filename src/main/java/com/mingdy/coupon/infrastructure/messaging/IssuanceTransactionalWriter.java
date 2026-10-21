package com.mingdy.coupon.infrastructure.messaging;


import com.mingdy.coupon.domain.CouponRepository;
import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.domain.IssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// IssuanceWriter와 분리한 이유: UNIQUE 위반 예외를 "트랜잭션 경계 바깥"에서 잡기 위해서.
// 1. write()에 @Transactional을 붙이고 안에서 catch하면 안 됨
//    - catch로 잡아도 트랜잭션이 rollback-only로 마킹돼 커밋 때 UnexpectedRollbackException 발생
//    - (참고) ID 전략이 IDENTITY가 아니면 쓰기 지연으로 INSERT가 커밋(메서드 종료 후) 때 나가서
//      catch가 아예 못 잡음. 지금은 IDENTITY라 save() 시점에 INSERT가 즉시 나감
//    → 둘 다 워커까지 예외가 올라가 재처리됨
// 2. 같은 클래스에 @Transactional 메서드를 두고 this.method()로 부르면 안 됨
//    - self-invocation은 Spring 프록시를 안 거쳐서 @Transactional이 무시됨
// 그래서 별도 빈으로 분리해 프록시를 통해 호출함.
// @Transactional은 프록시를 거쳐서 호출될 때만 동작하기 때문!
//    - @Transactional 자체는 표시일 뿐, 트랜잭션 시작/커밋/롤백은 Spring이 감싼 프록시가 함
//    - 다른 빈에서 주입받아 호출 → 프록시 통과 → 동작 O
//    - 같은 클래스에서 this.method() → this는 프록시 속 진짜 객체라 프록시를 안 거침 → 동작 X
//    - private 메서드 → 프록시가 가로챌 수 없음 → 동작 X
//    - new로 직접 만든 객체 → Spring 빈이 아니라 프록시가 없음 → 동작 X
//    (AspectJ 모드면 프록시 없이 동작하지만 기본 설정 아님)
@Component
@RequiredArgsConstructor
public class IssuanceTransactionalWriter {

    private final IssuanceRepository issuanceRepository;
    private final CouponRepository couponRepository;

    @Transactional
    public void insertAndIncrement(IssuanceRequested event) {
        issuanceRepository.save(
                new Issuance(
                        event.getUserId(),
                        event.getCouponId(),
                        event.getIssuedAt(),
                        event.getExpiresAt()
                )
        );
        couponRepository.incrementIssuedQuantity(event.getCouponId());
    }

}
