package com.mingdy.coupon.application;

// 대기실 판정 결과. 통과는 ADMITTED 하나로 충분하고, 대기 중이면 waiting() 으로 만든다.
public record Admission (
        boolean admitted,
        long position,
        long estimatedWaitSeconds
) {
    public static final Admission ADMITTED = new Admission(true, 0, 0);

    // 예상 대기시간은 position / admitPerSecond 의 올림. 1초에 나가는 인원 단위로 끊는다.
    public static Admission waiting(long position, int admitPerSecond) {
        return new Admission(false, position, (position + admitPerSecond - 1) / admitPerSecond);
    }

    // admitPerSecond = 100/s
    // 1~100 -> 1초
    // (1 + 100 -1) / 100 = 1
    // (50 + 100 -1) / 100 = 1
    // (100 + 100 -1) / 100 = 1

    // 101~200 -> 2초
    // (101 + 100 -1) / 100 = 2
    // (150 + 100 -1) / 100 = 2
    // (200 + 100 -1) / 100 = 2
}