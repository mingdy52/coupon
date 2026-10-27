package com.mingdy.coupon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "issuance_dlt_log",
        indexes = {
                @Index(name = "idx_issuance_dlt_message_key", columnList = "message_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuanceDltLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_key", nullable = false, length = 300)
    private String messageKey; // dlt 로그마다 어떤 키가 있음

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // 페이로드부터 받은 거에 이벤트의 내용을 꺼내 리플레이 할 때 재조립

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssuanceDltStatus status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;


    public IssuanceDltLog(
            String messageKey,
            String payload,
            String errorMessage,
            LocalDateTime receivedAt
    ) {
        this(null, messageKey, payload, errorMessage, IssuanceDltStatus.PENDING, receivedAt);
    }

    public IssuanceDltLog(
            Long id,
            String messageKey,
            String payload,
            String errorMessage,
            IssuanceDltStatus status,
            LocalDateTime receivedAt

    ) {
        this.id = id;
        this.messageKey = messageKey;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public void setStatus(IssuanceDltStatus status) {
        this.status = status;
    }
}