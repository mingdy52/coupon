package com.mingdy.coupon.application;

import com.mingdy.coupon.infrastructure.cache.WaitingRoomEntry;
import com.mingdy.coupon.infrastructure.cache.WaitingRoomProperties;
import com.mingdy.coupon.infrastructure.cache.WaitingRoomRedisRepository;
import com.mingdy.coupon.support.WaitingRoomNotEnteredException;
import com.mingdy.coupon.application.WaitingRoom;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisWaitingRoom implements WaitingRoom {

    private final WaitingRoomRedisRepository waitingRoomRedisRepository;
    private final WaitingRoomProperties waitingRoomProperties;

    @Override
    public Admission enter(long couponId, long userId) {
        WaitingRoomEntry entry = waitingRoomRedisRepository.enter(couponId, userId);
        return entry.admitted()
                ? Admission.ADMITTED
                : Admission.waiting(entry.position(), waitingRoomProperties.getAdmitPerSecond());
    }

    @Override
    public Admission status(long couponId, long userId) {
        Long position = waitingRoomRedisRepository.status(couponId, userId);
        // 입장권이 TTL 로 만료된 사용자도 줄에서 이미 빠져 있어 여기로 온다.
        if (position == null) {
            throw new WaitingRoomNotEnteredException();
        }
        if (position == 0L) {
            return Admission.ADMITTED;
        }
        return Admission.waiting(position, waitingRoomProperties.getAdmitPerSecond());
    }

    @Override
    public boolean isAdmitted(long couponId, long userId) {
        return waitingRoomRedisRepository.isAdmitted(couponId, userId);
    }

    // 배출 타이머. ShedLock 으로 매초 한 대만 돈다 (안 그러면 통과 속도가 서버 수만큼 곱해진다).
    // lockAtLeastFor: 배출이 금방 끝나도 락을 잡아 두어 서버 간 시각차로 같은 1초에 또 배출하지 않게 한다.
    // 1초보다 짧아야 다음 틱에 락을 다시 잡을 수 있다.
    @Scheduled(fixedRate = 1000)
    @SchedulerLock(name = "waiting-room-drain", lockAtLeastFor = "PT0.95S")
    public void drain() {
        for (Long couponId : waitingRoomRedisRepository.activeRooms()) {
            waitingRoomRedisRepository.drain(
                    couponId,
                    waitingRoomProperties.getAdmitPerSecond(),
                    waitingRoomProperties.getPassTtlMs()
            );
        }
    }
}