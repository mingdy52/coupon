package com.mingdy.coupon.infrastructure.cache;

/**
 * 대기실 진입 결과. 통과면 position 은 0.
 */
public record WaitingRoomEntry(boolean admitted, long position) {
}