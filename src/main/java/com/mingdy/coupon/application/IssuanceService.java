package com.mingdy.coupon.application;

import com.mingdy.coupon.domain.Issuance;
import com.mingdy.coupon.domain.IssuanceRepository;
import com.mingdy.coupon.support.AlreadyUsedException;
import com.mingdy.coupon.support.ExpiredException;
import com.mingdy.coupon.support.IssuanceNotFoundException;
import com.mingdy.coupon.support.NotOwnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssuanceService {

    private final IssuanceRepository issuanceRepository;

    @Transactional
    public Issuance use(Long issuanceId, Long userId) {
        Issuance issuance = issuanceRepository.findById(issuanceId)
                .orElseThrow(IssuanceNotFoundException::new);

        if(issuance.getUserId() != userId) {
            throw new NotOwnerException();
        }

        switch (issuance.getStatus()) {
            case USED -> throw new AlreadyUsedException();
            case EXPIRED -> throw new ExpiredException();
            case ISSUED -> {}
        }

        LocalDateTime now = LocalDateTime.now();
        if (issuance.isExpired(now)) {
            throw new ExpiredException();
        }

        issuance.markUsed(now);
        return issuance;
    }

    @Transactional(readOnly = true)
    public List<Issuance> findByUser(long userId) {
        return issuanceRepository.findByUserIdOrderByIssuedAtDesc(userId);
    }
}
