package com.mingdy.coupon.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@Component
@RequiredArgsConstructor
public class IssuanceEventHandler {

    private final IssuanceWriter issuanceWriter;

    // 인메모리 Worker 의 큐 polling 루프는 `@EventListener`(이벤트 수신)와 `@Async`(별도 스레드 실행)가 대신하고
    // 핵심 로직(write)만 남는다.
    // write 에서 예외가 나면 호출자에게 전달되지 않고 AsyncUncaughtExceptionHandler 가 로그만 남긴다(재시도 없음).
    @Async(AsyncIssuanceConfig.ISSUANCE_TASK_EXECUTOR) // issuanceTaskExecutor 스레드에서 동작
    @EventListener
    public void handle(IssuanceRequested event) {
        issuanceWriter.write(event);
    }

}
