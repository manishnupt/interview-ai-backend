package com.aiinterview.backend.magiclink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkStatusResponse {
    private String candidateName;
    private Long jobId;
    private String status;
    private String statusLabel;
    private String statusDescription;
    private Integer screeningScore;
    private Integer interviewScore;
    private String recommendation;
    private LocalDateTime appliedAt;
}
