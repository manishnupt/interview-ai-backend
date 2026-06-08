package com.aiinterview.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserId(Long userId);

    void deleteAllByUserId(Long userId);

    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
