package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimitDto {
    private Integer monthlyInterviewCap;
    private Integer monthlyScreeningCap;
    private BigDecimal monthlyCostCapUsd;
    private Integer alertThresholdPct;
}
