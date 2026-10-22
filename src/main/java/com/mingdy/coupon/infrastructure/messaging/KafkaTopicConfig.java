package com.mingdy.coupon.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// 애플리케이션이 시작할 때 이 토픽들이 카프카에 없으면 새로 만든다.
// NewTopic 빈을 등록해두면 스프링 부트가 자동으로 만든 KafkaAdmin 이 브로커에 접속해 생성한다.
// 이미 있으면 그대로 둔다.
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic issuanceRequestedTopic() {
        return TopicBuilder.name(IssuanceTopics.REQUESTED)
                // 파티션 3개 = 컨슈머 최대 3개가 병렬로 처리할 수 있다. (IssuanceWorker 의 concurrency = 3 과 맞춤)
                .partitions(3)
                // 복제본 수. 같은 파티션을 몇 대의 브로커에 복사해 둘지.
                // 브로커가 1대뿐이라 1 이 최대다. 운영에서는 보통 3으로 둬서 브로커 1대가 죽어도 데이터가 남게 한다.
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic issuanceRequestedDltTopic() {
        // 실패한 메시지를 원래 파티션 번호 그대로 DLT 에 넣으므로(KafkaErrorHandlerConfig) 파티션 수를 원본과 맞춘다.
        // 원본보다 적으면 없는 파티션 번호로 보내려다 실패한다.
        return TopicBuilder.name(IssuanceTopics.REQUESTED_DLT).partitions(3).replicas(1).build();
    }
}
