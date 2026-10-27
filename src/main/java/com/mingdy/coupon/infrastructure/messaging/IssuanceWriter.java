package com.mingdy.coupon.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class IssuanceWriter {

    private static final Logger log = LoggerFactory.getLogger(IssuanceWriter.class);

    private final IssuanceTransactionalWriter issuanceTransactionalWriter;

    public IssuanceWriter(IssuanceTransactionalWriter issuanceTransactionalWriter) {
        this.issuanceTransactionalWriter = issuanceTransactionalWriter;
    }

    public void write(IssuanceRequested event) {
        try {
            issuanceTransactionalWriter.insertAndIncrement(event);
        } catch (DataIntegrityViolationException e) {
            if (!issuanceTransactionalWriter.isAlreadyApplied(event)) {
                throw e;
            }

            // DataIntegrityViolationException: 중복 삽입이면 unique로 걸릴 때 발생하는 에러
            // 에러를 그대로 내보내면 워커가 재처리를 시도함.
            // 트랜잭션이 이미 끝난(롤백된) 뒤 바깥에서 잡는 구조라 여기서 삼켜도 안전함.
            // 분리 이유는 IssuanceTransactionalWriter 주석 참고.

            log.debug(
                    "UNIQUE 위반은 멱등 처리: couponId={}, userId={}",
                    event.getCouponId(),
                    event.getUserId()
            );
        }
    }

}
