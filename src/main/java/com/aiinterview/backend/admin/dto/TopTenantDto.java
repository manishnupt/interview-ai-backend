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
public class TopTenantDto {
    private Long id;
    private String name;
    private BigDecimal costThisMonthUsd;
    private Integer interviewsThisMonth;
}
