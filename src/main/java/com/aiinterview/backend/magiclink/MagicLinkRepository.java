package com.aiinterview.backend.magiclink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagicLinkRepository extends JpaRepository<MagicLink, Long> {

    Optional<MagicLink> findByToken(String token);
}
