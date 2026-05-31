package com.aiinterview.backend.jobs.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobRequest {

    private String title;

    @Size(min = 50)
    private String description;

    @Min(0)
    @Max(30)
    private Integer requiredExperienceYears;

    private String requiredSkills;

    @Min(1)
    @Max(10)
    private Integer screeningThreshold;

    @Min(1)
    @Max(50)
    private Integer openingsCount;
}
