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
public class CandidateSummaryResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String status;
    private String jobTitle;
    private Integer screeningScore;
    private Integer interviewScore;
    private String recommendation;
    private LocalDateTime appliedAt;
}
