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
public class InterviewReportDto {

    private Integer score;
    private String strengths;
    private String weaknesses;
    private String recommendation;
    private String summary;
    private String fullTranscript;
    private LocalDateTime interviewedAt;
}
