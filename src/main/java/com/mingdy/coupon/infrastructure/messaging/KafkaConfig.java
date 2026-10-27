package com.mingdy.coupon.infrastructure.messaging;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.JacksonMapperUtils;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

// 프로듀서(보내는 쪽)와 컨슈머(받는 쪽)를 어떻게 만들지 정하는 설정.
//
// 카프카는 메시지를 바이트 배열로만 주고받는다. 그래서
// - 보낼 때: 자바 객체 → 바이트 (직렬화, Serializer)
// - 받을 때: 바이트 → 자바 객체 (역직렬화, Deserializer)
// 를 키와 값 각각에 지정해줘야 한다. 여기서는 키는 문자열(userId), 값은 JSON 으로 한다.
//
// 이 빈들을 직접 만들었기 때문에 스프링 부트의 자동 설정은 꺼진다.
// → application.yaml 의 spring.kafka.producer.* / consumer.* / listener.* 는 적어도 반영되지 않는다.
//   설정을 바꾸려면 이 파일을 고쳐야 한다.
@Configuration
@EnableKafka // @KafkaListener 를 찾아서 동작하게 해준다
public class KafkaConfig {

    // 브로커 주소. 처음 접속할 주소이고, 접속하면 브로커가 나머지 정보를 알려준다.
    // 로컬 실행: localhost:9094 / 도커 안에서 실행: kafka:9092 (docker-compose.yml 참고)
    private final String bootstrapServers;
    // 객체 ↔ JSON 변환기. LocalDateTime 같은 날짜 타입도 처리할 수 있게 설정된 걸 쓴다.
    private final JsonMapper jsonMapper = JacksonMapperUtils.enhancedJsonMapper();

    public KafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    // ===== 프로듀서(보내는 쪽) =====

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                // acks: 브로커가 "받았다"고 답하는 기준.
                // - "0": 답을 기다리지 않음. 가장 빠르지만 유실돼도 모름
                // - "1": 리더 파티션이 저장하면 응답. 복제본에 복사되기 전에 리더가 죽으면 유실될 수 있음
                // - "all": 복제본까지 모두 저장해야 응답. 가장 안전하고 가장 느림
                // 지금은 브로커가 1대(복제본 없음)라 "1"과 "all"의 차이가 없다.
                ProducerConfig.ACKS_CONFIG, "1"
        );
        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(), // 키: String → 바이트
                // 값: 객체 → JSON 바이트.
                // 이때 메시지 헤더(__TypeId__)에 클래스 이름(com.mingdy...IssuanceRequested)도 같이 적어 보낸다.
                new JacksonJsonSerializer<>(jsonMapper)
        );
    }

    // IssuanceRequestProducer 가 메시지를 보낼 때 쓰는 도구
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ===== 컨슈머(받는 쪽) =====

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, IssuanceTopics.CONSUMER_GROUP,
                // 이 그룹이 처음 접속해서 저장된 오프셋이 없을 때 어디서부터 읽을지.
                // - "earliest": 토픽에 남아있는 가장 처음 메시지부터 (앱이 늦게 떠도 쌓인 요청을 다 처리)
                // - "latest"(기본값): 지금 이후 들어오는 메시지부터 (그 전에 쌓인 건 건너뜀)
                // 한 번 오프셋이 저장된 뒤에는 이 값과 상관없이 저장된 위치부터 읽는다.
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        // 값: JSON 바이트 → IssuanceRequested 객체
        JacksonJsonDeserializer<IssuanceRequested> jsonDelegate =
                new JacksonJsonDeserializer<>(IssuanceRequested.class, jsonMapper);
        // 헤더(__TypeId__)에 적힌 클래스로 객체를 만드는데, 아무 클래스나 만들면 보안상 위험하다.
        // 그래서 허용할 패키지를 지정한다. 이 패키지가 틀리면 모든 메시지가 역직렬화에 실패해 DLT 로 간다.
        jsonDelegate.addTrustedPackages("com.mingdy.coupon.infrastructure.messaging");
        // ErrorHandlingDeserializer: 실제 역직렬화를 jsonDelegate 에 맡기고, 실패하면 예외를 잡아둔다.
        // 이게 없으면 깨진 메시지가 리스너에 도달하기 전에 에러가 나서 같은 메시지를 무한히 다시 읽는다.
        // 이게 있으면 에러 핸들러(KafkaErrorHandlerConfig)로 넘어가 DLT 로 치워진다.
        @SuppressWarnings("unchecked")
        ErrorHandlingDeserializer<Object> valueDeserializer =
                (ErrorHandlingDeserializer<Object>) (ErrorHandlingDeserializer<?>)
                        new ErrorHandlingDeserializer<>(jsonDelegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    // @KafkaListener 가 붙은 메서드를 실행해줄 "리스너 컨테이너"를 만드는 공장.
    // 컨테이너가 하는 일: 백그라운드 스레드에서 브로커에 메시지 요청(poll) → 리스너 메서드 호출 → 오프셋 커밋.
    // 빈 이름이 kafkaListenerContainerFactory 이면 @KafkaListener 가 기본으로 이걸 쓴다.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 리스너에서 예외가 나면 이 핸들러가 재시도 / DLT 전송을 맡는다.
        factory.setCommonErrorHandler(errorHandler);
        // 오프셋 커밋은 따로 설정하지 않았다. 기본값은 poll 로 가져온 메시지 묶음을 다 처리한 뒤 커밋한다.
        // (카프카 클라이언트의 "몇 초마다 자동 커밋"은 스프링이 꺼둔다 → 처리 전에 커밋돼서 유실되는 걸 막음)
        return factory;
    }

    @Bean
    public ConsumerFactory<String, byte[]> dltConsumerFactory() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "issuance-dlt-log",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ByteArrayDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> dltConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dltConsumerFactory);
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(5_000L, FixedBackOff.UNLIMITED_ATTEMPTS))
        );
        return factory;
    }

}
