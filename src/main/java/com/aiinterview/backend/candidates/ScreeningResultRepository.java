package com.aiinterview.backend.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {

    Optional<ScreeningResult> findByCandidateId(Long candidateId);
}
