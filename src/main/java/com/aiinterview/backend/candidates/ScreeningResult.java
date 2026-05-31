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
@Table(name = "screening_results")
public class ScreeningResult extends BaseEntity {

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private int score;

    @Column(name = "match_percentage", nullable = false)
    private int matchPercentage;

    @Column(nullable = false)
    private boolean fit;

    @Column(name = "fit_reasons", columnDefinition = "TEXT")
    private String fitReasons;

    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt;
}
