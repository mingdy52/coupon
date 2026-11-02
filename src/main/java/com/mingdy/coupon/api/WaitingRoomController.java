package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.WaitingRoomResponse;
import com.mingdy.coupon.application.WaitingRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoom waitingRoom;

    // 대기열 등록. 다시 호출해도 순번은 그대로다.
    @PostMapping("/{couponId}")
    public WaitingRoomResponse enter(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return WaitingRoomResponse.from(waitingRoom.enter(couponId, userId));
    }

    // 폴링용 상태 조회. 등록하지 않으므로 줄에 없으면 404.
    @GetMapping("/{couponId}")
    public WaitingRoomResponse status(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return WaitingRoomResponse.from(waitingRoom.status(couponId, userId));
    }
}
