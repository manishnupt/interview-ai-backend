package com.aiinterview.backend.usage;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsageAggregationService {

    private final UsageRecordRepository usageRecordRepository;
    private final UsageDailySummaryRepository usageDailySummaryRepository;

    /**
     * Runs every night at 1 AM. Aggregates yesterday's
     * usage_records into usage_daily_summary, one row per company.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void runDailyAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        aggregateForDate(yesterday);
    }

    public void aggregateForDate(LocalDate date) {
        System.out.println("[UsageAgg] Aggregating usage for: " + date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Long> companyIds = usageRecordRepository
            .findDistinctCompanyIdsInRange(startOfDay, endOfDay);

        if (companyIds.isEmpty()) {
            System.out.println("[UsageAgg] No usage data for: " + date);
            return;
        }

        for (Long companyId : companyIds) {
            try {
                aggregateForCompanyAndDate(companyId, date, startOfDay, endOfDay);
            } catch (Exception e) {
                System.out.println("[UsageAgg] Failed for company "
                    + companyId + ": " + e.getMessage());
            }
        }

        System.out.println("[UsageAgg] Completed for "
            + companyIds.size() + " companies on " + date);
    }

    private void aggregateForCompanyAndDate(
            Long companyId, LocalDate date,
            LocalDateTime startOfDay, LocalDateTime endOfDay) {

        List<UsageRecord> records = usageRecordRepository
            .findAllByCompanyIdAndCreatedAtBetween(companyId, startOfDay, endOfDay);

        int totalInterviews = (int) records.stream()
            .filter(r -> "interview".equals(r.getUsageType()))
            .count();
        int totalScreenings = (int) records.stream()
            .filter(r -> "screening".equals(r.getUsageType()))
            .count();
        int failedCount = (int) records.stream()
            .filter(r -> r.getOutcome() != null
                && !r.getOutcome().equals("completed"))
            .count();

        double totalMinutes = records.stream()
            .mapToDouble(r -> r.getDurationSeconds() != null
                ? r.getDurationSeconds() / 60.0 : 0)
            .sum();

        long totalTtsChars = records.stream()
            .mapToLong(r -> r.getTtsCharacters() != null
                ? r.getTtsCharacters() : 0)
            .sum();

        long totalSttWords = records.stream()
            .mapToLong(r -> r.getSttWords() != null
                ? r.getSttWords() : 0)
            .sum();

        long totalLlmTokens = records.stream()
            .mapToLong(r -> (r.getLlmInputTokens() != null
                ? r.getLlmInputTokens() : 0)
                + (r.getLlmOutputTokens() != null
                ? r.getLlmOutputTokens() : 0))
            .sum();

        BigDecimal totalCost = records.stream()
            .map(r -> r.getTotalCostUsd() != null
                ? r.getTotalCostUsd() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalCalls = totalInterviews + totalScreenings;
        BigDecimal avgCost = totalCalls > 0
            ? totalCost.divide(BigDecimal.valueOf(totalCalls), 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        UsageDailySummary summary = usageDailySummaryRepository
            .findByCompanyIdAndSummaryDate(companyId, date)
            .orElse(new UsageDailySummary());

        summary.setCompanyId(companyId);
        summary.setSummaryDate(date);
        summary.setTotalInterviews(totalInterviews);
        summary.setTotalScreenings(totalScreenings);
        summary.setTotalMinutes(totalMinutes);
        summary.setTotalTtsCharacters(totalTtsChars);
        summary.setTotalSttWords(totalSttWords);
        summary.setTotalLlmTokens(totalLlmTokens);
        summary.setFailedCount(failedCount);
        summary.setTotalCostUsd(totalCost);
        summary.setAvgCostPerInterviewUsd(avgCost);

        usageDailySummaryRepository.save(summary);

        System.out.println("[UsageAgg] Company " + companyId
            + " | " + date + " | interviews=" + totalInterviews
            + " | cost=$" + totalCost);
    }
}
