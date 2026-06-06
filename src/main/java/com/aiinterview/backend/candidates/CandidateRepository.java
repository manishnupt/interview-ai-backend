package com.aiinterview.backend.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findAllByCompanyId(Long companyId);

    List<Candidate> findAllByJobId(Long jobId);

    List<Candidate> findAllByJobIdAndCompanyId(Long jobId, Long companyId);

    List<Candidate> findAllByStatus(CandidateStatus status);

    List<Candidate> findAllByStatusAndCompanyId(CandidateStatus status, Long companyId);

    List<Candidate> findAllByCompanyIdAndStatus(Long companyId, CandidateStatus status);

    Optional<Candidate> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByEmailAndJobId(String email, Long jobId);
}
