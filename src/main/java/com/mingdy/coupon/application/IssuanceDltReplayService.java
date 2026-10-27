package com.mingdy.coupon.application;

import com.mingdy.coupon.domain.IssuanceDltLog;
import com.mingdy.coupon.domain.IssuanceDltLogRepository;
import com.mingdy.coupon.domain.IssuanceDltStatus;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequestProducer;
import com.mingdy.coupon.infrastructure.messaging.IssuanceRequested;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class IssuanceDltReplayService {

    private static final int MAX_REPLAY_COUNT = 100;

    private final IssuanceDltLogRepository issuanceDltLogRepository;
    private final IssuanceRequestProducer issuanceRequestProducer;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Transactional
    public int replay(List<Long> ids) {
        List<Long> selectedIds = ids.stream().distinct().toList();
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("At least one DLT log id is required");
        }
        if (selectedIds.size() > MAX_REPLAY_COUNT) {
            throw new IllegalArgumentException(
                    "At most " + MAX_REPLAY_COUNT + " DLT logs can be replayed");
        }

        List<IssuanceDltLog> logs = issuanceDltLogRepository.findAllByIdForUpdate(selectedIds).stream()
                .sorted(Comparator.comparing(IssuanceDltLog::getReceivedAt))
                .toList();
        if (logs.size() != selectedIds.size()) {
            throw new IllegalStateException("Some DLT logs were not found");
        }

        List<IssuanceRequested> events = new ArrayList<>();
        for (IssuanceDltLog log : logs) {
            if (log.getStatus() != IssuanceDltStatus.PENDING) {
                throw new IllegalStateException(
                        "Only pending DLT logs can be replayed: " + log.getId());
            }
            events.add(toEvent(log));
        }
        for (int i = 0; i < logs.size(); i++) {
            issuanceRequestProducer.publishAndWait(events.get(i));
            logs.get(i).setStatus(IssuanceDltStatus.REPLAYED);
        }
        return logs.size();
    }

    private IssuanceRequested toEvent(IssuanceDltLog log) {
        return jsonMapper.readValue(log.getPayload(), IssuanceRequested.class);
    }
}