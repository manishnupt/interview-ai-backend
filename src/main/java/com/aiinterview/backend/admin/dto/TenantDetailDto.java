package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDetailDto {
    private Long id;
    private String name;
    private String slug;
    private String plan;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<TenantUserDto> users;
    private List<TenantJobSummaryDto> jobs;
    private List<UsageDailySummaryDto> usageHistory;
    private CostBreakdownDto costBreakdown;
    private PlanLimitDto planLimit;
}
