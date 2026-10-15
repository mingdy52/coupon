package com.mingdy.coupon.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
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



}
