package com.mingdy.coupon.infrastructure.messaging;

// [카프카 기본 용어]
// - 브로커(broker): 카프카 서버. 메시지를 디스크에 저장하고 꺼내준다. docker-compose 의 kafka 컨테이너 1대.
// - 토픽(topic): 메시지를 담는 이름 붙은 통. DB 의 테이블 같은 개념.
// - 파티션(partition): 토픽을 쪼갠 조각. 토픽 하나에 파티션이 여러 개 있고 메시지는 그중 하나에 들어간다.
//   파티션 안에서는 들어온 순서가 보장되고, 파티션끼리는 순서 보장이 없다.
// - 오프셋(offset): 파티션 안에서 메시지의 번호(0, 1, 2, ...). "어디까지 읽었나"를 이 번호로 기억한다.
// - 프로듀서(producer): 메시지를 보내는 쪽 → IssuanceRequestProducer
// - 컨슈머(consumer): 메시지를 읽는 쪽 → IssuanceWorker
//
// 큐와 다른 점: 메시지는 읽어도 지워지지 않는다(보관 기간 동안 디스크에 남음).
// 컨슈머는 "어디까지 읽었는지(오프셋)"만 브로커에 기록하고, 다음엔 그 다음 번호부터 읽는다.
// 그래서 앱이 죽어도 메시지가 브로커에 남아 있어 재시작하면 이어서 처리한다. (인메모리 큐와의 핵심 차이)
public final class IssuanceTopics {

    // 쿠폰 발급 요청이 쌓이는 토픽
    public static final String REQUESTED = "issuance.requested";

    // DLT(Dead Letter Topic): 재시도해도 계속 실패한 메시지를 옮겨두는 토픽.
    // 실패한 메시지 하나 때문에 뒤의 메시지가 전부 막히지 않도록 치워두는 곳이다.
    // 나중에 kafka_dlt_peek.sh 로 무엇이 왜 실패했는지 확인할 수 있다. (KafkaErrorHandlerConfig 참고)
    public static final String REQUESTED_DLT = "issuance.requested.DLT";

    // 컨슈머 그룹: 같은 일을 나눠서 하는 컨슈머들의 묶음.
    // - 그룹 안에서 파티션 하나는 컨슈머 하나만 맡는다. 파티션 3개 + 컨슈머 3개 → 하나씩 나눠 맡아 병렬 처리.
    // - 어디까지 읽었는지(오프셋)는 "그룹 단위 + 파티션 단위"로 브로커에 저장된다.
    //   (브로커 내부의 __consumer_offsets 라는 토픽에 저장)
    // - 그룹 이름이 다르면 같은 토픽을 처음부터 따로 읽는다. 예: 발급 저장 그룹, 알림 발송 그룹이 각자 전부 읽음.
    public static final String CONSUMER_GROUP = "issuance-worker";

}
