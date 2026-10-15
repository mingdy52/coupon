package com.mingdy.coupon.domain;

import jakarta.persistence.*;

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

}
