package com.aiinterview.backend.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    @Query("SELECT DISTINCT r.companyId FROM UsageRecord r WHERE r.createdAt >= :start AND r.createdAt < :end")
    List<Long> findDistinctCompanyIdsInRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<UsageRecord> findAllByCompanyIdAndCreatedAtBetween(
            Long companyId, LocalDateTime start, LocalDateTime end);

    List<UsageRecord> findAllByCompanyId(Long companyId);
}
