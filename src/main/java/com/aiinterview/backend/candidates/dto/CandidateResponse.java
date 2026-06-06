package com.aiinterview.backend.candidates.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String name;
    private String email;
    private String phone;
    private String resumeUrl;
    private String status;
    private LocalDateTime appliedAt;
    private ScreeningResultDto screeningResult;
    private InterviewReportDto interviewReport;
    private List<HrNoteResponse> notes;
}
