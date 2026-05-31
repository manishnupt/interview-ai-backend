package com.aiinterview.backend.candidates;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "hr_notes")
public class HrNote extends BaseEntity {

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
