package com.aiinterview.backend.jobs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummaryResponse {

    private Long id;
    private String title;
    private String status;
    private String slug;
    private Integer openingsCount;
    private Integer screeningThreshold;
    private Integer applicantCount;
    private Integer shortlistedCount;
    private LocalDateTime createdAt;
}
