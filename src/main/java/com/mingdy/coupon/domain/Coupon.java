package com.mingdy.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Coupon(
            String name,
            int totalQuantity,
            int validityDays,
            LocalDateTime startsAt
    ) {
        this(null, name, totalQuantity, validityDays, startsAt, 0, LocalDateTime.now());
    }

    public Coupon(
            Long id,
            String name,
            int totalQuantity,
            int validityDays,
            LocalDateTime startsAt,
            int issuedQuantity,
            LocalDateTime createdAt
    ) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.validityDays = validityDays;
        this.startsAt = startsAt;
        this.issuedQuantity = issuedQuantity;
        this.createdAt = createdAt;
        this.id = id;
    }

    public boolean isBookingOpen(LocalDateTime now) {
        return startsAt == null || !now.isBefore(startsAt);
    }

    public boolean isSoldOut() {
        return issuedQuantity >= totalQuantity;
    }

    public void incrementIssuedQuantity() {
        this.issuedQuantity++;
    }
}
