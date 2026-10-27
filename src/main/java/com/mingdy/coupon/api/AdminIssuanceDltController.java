package com.mingdy.coupon.api;

import com.mingdy.coupon.api.dto.IssuanceDltReplayResponse;
import com.mingdy.coupon.api.dto.IssuanceDltResponse;
import com.mingdy.coupon.api.dto.ReplayIssuanceDltRequest;
import com.mingdy.coupon.application.IssuanceDltReplayService;
import com.mingdy.coupon.application.IssuanceDltService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/issuance/dlt")
@RequiredArgsConstructor
public class AdminIssuanceDltController {

    private final IssuanceDltService issuanceDltService;
    private final IssuanceDltReplayService issuanceDltReplayService;

    @GetMapping
    public List<IssuanceDltResponse> findRecent() {
        return issuanceDltService.findRecent().stream()
                .map(IssuanceDltResponse::from)
                .toList();
    }

    @PostMapping("/replay")
    public IssuanceDltReplayResponse replay(@RequestBody ReplayIssuanceDltRequest request) {
        return new IssuanceDltReplayResponse(issuanceDltReplayService.replay(request.ids()));
    }

}

