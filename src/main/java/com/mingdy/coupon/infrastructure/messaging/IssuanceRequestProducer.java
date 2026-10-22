package com.mingdy.coupon.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 프로듀서: 발급 요청을 카프카 토픽에 보낸다.
// 인메모리 큐의 enqueue(), 스프링 이벤트의 publishEvent() 자리에 해당한다.
@Component
@RequiredArgsConstructor
public class IssuanceRequestProducer {

    // KafkaTemplate: 스프링이 제공하는 "카프카로 메시지 보내기" 도구. KafkaConfig 에서 빈으로 만든다.
    // <String, Object> = <키 타입, 값 타입>
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(IssuanceRequested event) {
        // send(토픽, 키, 값)
        // - 키: 어느 파티션에 넣을지 정하는 기준. 같은 키는 항상 같은 파티션으로 간다(키의 해시값으로 결정).
        //   userId 를 키로 쓰면 같은 유저의 요청은 한 파티션에 들어가 순서대로 처리된다.
        //   키가 없으면 파티션에 골고루 흩어지고 순서는 보장되지 않는다.
        // - 값: 보낼 메시지 본문. KafkaConfig 의 JSON 직렬화기가 객체를 JSON 바이트로 바꿔 보낸다.
        //
        // 주의: send() 는 비동기다. 메시지를 버퍼에 넣고 바로 리턴하고, 실제 전송은 백그라운드에서 한다.
        // 리턴값(CompletableFuture)을 확인하지 않아서 전송이 실패해도 API 는 성공 응답을 준다.
        // 그 경우 Redis 재고는 이미 차감돼 있어 발급 1건이 유실된다.
        kafkaTemplate.send(IssuanceTopics.REQUESTED, String.valueOf(event.getUserId()), event);
    }

}
