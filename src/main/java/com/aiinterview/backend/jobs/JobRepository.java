package com.aiinterview.backend.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByCompanyId(Long companyId);

    Optional<Job> findBySlug(String slug);

    Optional<Job> findBySlugAndStatus(String slug, JobStatus status);

    Optional<Job> findByIdAndCompanyId(Long id, Long companyId);
}
