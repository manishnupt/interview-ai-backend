package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantListItemDto {
    private Long id;
    private String name;
    private String slug;
    private String plan;
    private Boolean isActive;
    private String status;
    private LocalDateTime createdAt;
    private Integer totalUsers;
    private Integer activeJobs;
    private Integer interviewsThisMonth;
    private Integer screeningsThisMonth;
    private BigDecimal costThisMonthUsd;
    private Integer monthlyInterviewCap;
    private BigDecimal monthlyCostCapUsd;
    private Integer usagePercentage;
    private String usageStatus;
}
