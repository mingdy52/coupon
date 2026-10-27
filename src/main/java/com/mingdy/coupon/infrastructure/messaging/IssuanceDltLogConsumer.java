package com.mingdy.coupon.infrastructure.messaging;

import com.mingdy.coupon.application.IssuanceDltService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IssuanceDltLogConsumer {

    private final IssuanceDltService issuanceDltService;

    @KafkaListener(
            topics = IssuanceTopics.REQUESTED_DLT,
            groupId = "issuance-dlt-log",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        issuanceDltService.record(record);
    }
}
