package com.aiinterview.backend.candidates.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResultDto {

    private Integer score;
    private Integer matchPercentage;
    private Boolean fit;
    private String fitReasons;
    private String concerns;
    private String missingSkills;
    private LocalDateTime screenedAt;
}
