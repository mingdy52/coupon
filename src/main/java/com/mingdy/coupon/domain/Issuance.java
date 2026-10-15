package com.mingdy.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "issuance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_issuance_user_coupon",
                        columnNames = {"user_id", "coupon_id"}
                )
        },
        indexes = {
                @Index(name = "idx_issuance_status", columnList = "status"),
                @Index(name = "idx_issuance_coupon", columnList = "coupon_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issuance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "coupon_id", nullable = false)
    private long couponId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssuanceStatus status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public Issuance(
            long userId,
            long couponId,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this(null, userId, couponId, issuedAt, expiresAt, IssuanceStatus.ISSUED, null);
    }

    public Issuance(
            Long id,
            long userId,
            long couponId,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt,
            IssuanceStatus status,
            LocalDateTime usedAt
    ) {
        this.userId = userId;
        this.couponId = couponId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.usedAt = usedAt;
        this.id = id;
    }


}
