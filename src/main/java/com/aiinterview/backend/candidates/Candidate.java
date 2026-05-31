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
@Table(name = "candidates")
public class Candidate extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "resume_url", columnDefinition = "TEXT")
    private String resumeUrl;

    @Column(name = "resume_s3_key", columnDefinition = "TEXT")
    private String resumeS3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CandidateStatus status;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
}
