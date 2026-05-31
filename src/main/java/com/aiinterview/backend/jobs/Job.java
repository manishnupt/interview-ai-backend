package com.aiinterview.backend.jobs;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jobs")
public class Job extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "required_experience_years", nullable = false)
    private int requiredExperienceYears;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(name = "screening_threshold", nullable = false)
    private int screeningThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "openings_count", nullable = false)
    private int openingsCount;

    @Column(name = "created_by")
    private Long createdBy;
}
