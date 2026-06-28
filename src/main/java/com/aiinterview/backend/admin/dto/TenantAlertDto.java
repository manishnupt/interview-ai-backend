package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAlertDto {
    private Long id;
    private String name;
    private Integer usagePercentage;
    private String usageStatus;
}
