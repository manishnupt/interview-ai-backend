package com.aiinterview.backend.usage;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "usage_daily_summary")
@Getter
@Setter
@NoArgsConstructor
public class UsageDailySummary extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_interviews")
    private Integer totalInterviews = 0;

    @Column(name = "total_screenings")
    private Integer totalScreenings = 0;

    @Column(name = "total_minutes")
    private Double totalMinutes = 0.0;

    @Column(name = "total_tts_characters")
    private Long totalTtsCharacters = 0L;

    @Column(name = "total_stt_words")
    private Long totalSttWords = 0L;

    @Column(name = "total_llm_tokens")
    private Long totalLlmTokens = 0L;

    @Column(name = "failed_count")
    private Integer failedCount = 0;

    @Column(name = "total_cost_usd")
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "avg_cost_per_interview_usd")
    private BigDecimal avgCostPerInterviewUsd = BigDecimal.ZERO;
}
