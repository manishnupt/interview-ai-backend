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
public class CostBreakdownDto {
    private BigDecimal twilioCostUsd;
    private BigDecimal deepgramCostUsd;
    private BigDecimal elevenlabsCostUsd;
    private BigDecimal openaiCostUsd;
    private BigDecimal totalCostUsd;
}
