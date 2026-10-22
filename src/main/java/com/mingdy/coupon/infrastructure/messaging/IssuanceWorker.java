package com.mingdy.coupon.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 컨슈머: 토픽에서 발급 요청을 꺼내 DB 에 저장한다.
// 인메모리 Worker 의 while + poll() 루프를 @KafkaListener 가 대신한다.
// 스프링이 백그라운드 스레드를 띄워 브로커에서 메시지를 계속 가져오고(poll), 한 건씩 consume() 을 호출한다.
@Component
@RequiredArgsConstructor
public class IssuanceWorker {

    private final IssuanceWriter issuanceWriter;

    @KafkaListener(
            topics = IssuanceTopics.REQUESTED,      // 이 토픽을 구독한다
            groupId = IssuanceTopics.CONSUMER_GROUP, // 이 그룹 이름으로 오프셋을 기록한다
            // 컨슈머 스레드 3개를 띄운다. 파티션이 3개라 스레드 하나가 파티션 하나씩 맡는다.
            // 파티션 수보다 많이 띄우면 남는 스레드는 맡을 파티션이 없어 놀게 된다.
            concurrency = "3"
    )
    public void consume(IssuanceRequested event) {
        // 정상 리턴 → 처리 완료로 보고 오프셋을 커밋한다("여기까지 읽었음" 기록).
        // 예외 발생 → 커밋하지 않고 KafkaErrorHandlerConfig 의 에러 핸들러가 재시도 → 그래도 실패하면 DLT 로 보낸다.
        //
        // 같은 메시지가 두 번 올 수도 있다(처리는 끝났는데 커밋 전에 앱이 죽은 경우 등).
        // IssuanceWriter 가 UNIQUE 위반을 무시하도록(멱등) 만들어 둬서 중복 저장되지 않는다.
        issuanceWriter.write(event);
    }

}
