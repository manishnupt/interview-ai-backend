package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageDailySummaryDto {
    private LocalDate date;
    private Integer totalInterviews;
    private Integer totalScreenings;
    private Double totalMinutes;
    private BigDecimal totalCostUsd;
}
