package com.mingdy.coupon.application;

import com.mingdy.coupon.domain.IssuanceDltLog;
import com.mingdy.coupon.domain.IssuanceDltLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssuanceDltService {

    private final IssuanceDltLogRepository issuanceDltLogRepository;

    @Transactional
    public void record(ConsumerRecord<String, byte[]> record) {
        String messageKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (issuanceDltLogRepository.existsByMessageKey(messageKey)) {
            return;
        }

        issuanceDltLogRepository.save(
                new IssuanceDltLog(
                        messageKey,
                        new String(record.value(), StandardCharsets.UTF_8),
                        header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                        LocalDateTime.now()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<IssuanceDltLog> findRecent() {
        return issuanceDltLogRepository.findTop100ByOrderByReceivedAtDesc();
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

}
