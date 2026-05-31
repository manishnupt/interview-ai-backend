package com.aiinterview.backend.magiclink;

import com.aiinterview.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "magic_links")
public class MagicLink extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;
}




















