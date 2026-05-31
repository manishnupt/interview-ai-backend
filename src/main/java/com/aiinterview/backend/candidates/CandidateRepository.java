package com.aiinterview.backend.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findAllByCompanyId(Long companyId);

    List<Candidate> findAllByJobId(Long jobId);

    List<Candidate> findAllByStatus(CandidateStatus status);

    List<Candidate> findAllByStatusAndCompanyId(CandidateStatus status, Long companyId);

    Optional<Candidate> findByIdAndCompanyId(Long id, Long companyId);
}
