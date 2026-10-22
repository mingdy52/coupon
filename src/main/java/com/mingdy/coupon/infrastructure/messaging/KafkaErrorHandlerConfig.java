package com.mingdy.coupon.infrastructure.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// IssuanceWorker.consume() 에서 예외가 났을 때 어떻게 할지 정한다.
//
// 이게 없으면? 카프카는 파티션 안에서 순서대로 읽기 때문에, 계속 실패하는 메시지 하나(poison pill)가 있으면
// 그 메시지를 넘기지 못하고 뒤의 메시지가 전부 막힐 수 있다.
// 그래서 "몇 번 재시도 → 그래도 안 되면 DLT 로 치우고 다음 메시지로 넘어간다"를 설정한다.
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // 재시도를 다 써도 실패하면 이 recoverer 가 메시지를 DLT 토픽으로 보낸다(전송에는 같은 KafkaTemplate 사용).
        // 보낼 위치: "원래토픽.DLT" 토픽의 원래와 같은 파티션 번호.
        // 실패 원인(예외 메시지, 스택트레이스)은 메시지 헤더에 같이 붙여준다 → kafka_dlt_peek.sh 로 확인 가능.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        // FixedBackOff(간격 ms, 재시도 횟수): 1초 간격으로 3번 재시도 → 처음 1번 + 재시도 3번 = 총 4번 시도.
        // 단, JSON 역직렬화 실패처럼 다시 해도 결과가 같은 예외는 재시도 없이 바로 DLT 로 간다.
        // (UNIQUE 위반은 IssuanceWriter 가 먼저 잡아서 여기까지 오지 않는다)
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }
}
