package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.IssuanceResponse;
import com.mingdy.coupon.application.IssuanceService;
import com.mingdy.coupon.domain.Issuance;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issuances")
@RequiredArgsConstructor
public class IssuanceController {

    private final IssuanceService issuanceService;

    @PostMapping("/{issuanceId}/use")
    public IssuanceResponse use(
            @PathVariable Long issuanceId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        Issuance issuance = issuanceService.use(issuanceId, userId);
        return IssuanceResponse.from(issuance);
    }

}
