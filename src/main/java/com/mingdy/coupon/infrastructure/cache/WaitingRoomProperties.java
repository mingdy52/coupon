package com.mingdy.coupon.infrastructure.cache;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coupon.waiting-room")
@Getter
public class WaitingRoomProperties {

    // 1초마다 통과시키는 인원.
    private final int admitPerSecond;
    // 입장권 유효 시간(ms). 이 안에 발급하지 않으면 다시 줄을 서야 한다.
    private final long passTtlMs;

    public WaitingRoomProperties(int admitPerSecond, long passTtlMs) {
        if (admitPerSecond <= 0) {
            throw new IllegalArgumentException("admitPerSecond must be positive");
        }
        if (passTtlMs <= 0) {
            throw new IllegalArgumentException("passTtlMs must be positive");
        }
        this.admitPerSecond = admitPerSecond;
        this.passTtlMs = passTtlMs;
    }

}