package com.aiinterview.backend.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {

    Optional<InterviewReport> findByCandidateId(Long candidateId);
}
