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
public class JobResponse {

    private Long id;
    private Long companyId;
    private String title;
    private String description;
    private Integer requiredExperienceYears;
    private String requiredSkills;
    private Integer screeningThreshold;
    private String status;
    private String slug;
    private Integer openingsCount;
    private String embedCode;
    private String applyUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer applicantCount;
    private Integer shortlistedCount;
}
