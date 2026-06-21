package com.aiinterview.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantJobSummaryDto {
    private Long id;
    private String title;
    private String status;
    private Integer applicantCount;
    private Integer shortlistedCount;
}
