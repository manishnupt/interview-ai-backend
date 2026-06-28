package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformDashboardDto {
    private Integer totalTenants;
    private Integer activeTenants;
    private Integer deactivatedTenants;
    private Integer archivedTenants;
    private Integer interviewsThisMonth;
    private Integer screeningsThisMonth;
    private BigDecimal totalCostThisMonthUsd;
    private List<TopTenantDto> topTenantsByCost;
    private List<UsageDailySummaryDto> platformUsageHistory;
    private List<TenantAlertDto> tenantsNearLimit;
}
