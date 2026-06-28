package com.aiinterview.backend.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageDailySummaryRepository extends JpaRepository<UsageDailySummary, Long> {

    Optional<UsageDailySummary> findByCompanyIdAndSummaryDate(Long companyId, LocalDate summaryDate);

    List<UsageDailySummary> findAllByCompanyIdAndSummaryDateBetween(
            Long companyId, LocalDate start, LocalDate end);

    List<UsageDailySummary> findAllBySummaryDateBetween(LocalDate start, LocalDate end);
}
