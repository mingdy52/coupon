package com.mingdy.coupon.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuanceDltLogRepository extends JpaRepository<IssuanceDltLog, Long> {

    boolean existsByMessageKey(String messageKey);

    List<IssuanceDltLog> findTop100ByOrderByReceivedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT log FROM IssuanceDltLog log WHERE log.id IN :ids")
    List<IssuanceDltLog> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}