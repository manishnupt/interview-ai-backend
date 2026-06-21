package com.aiinterview.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class UpdatePlanRequest {

    @NotBlank
    private String plan;

    private Integer monthlyInterviewCap;
    private Integer monthlyScreeningCap;
    private BigDecimal monthlyCostCapUsd;
    private Integer alertThresholdPct;
}
