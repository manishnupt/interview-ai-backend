package com.aiinterview.backend.candidates;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "interview_reports")
public class InterviewReport extends BaseEntity {

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(length = 50)
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "full_transcript", columnDefinition = "TEXT")
    private String fullTranscript;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "interviewed_at", nullable = false)
    private LocalDateTime interviewedAt;
}
