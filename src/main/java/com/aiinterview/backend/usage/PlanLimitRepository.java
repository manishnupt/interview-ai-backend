package com.aiinterview.backend.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanLimitRepository extends JpaRepository<PlanLimit, Long> {

    Optional<PlanLimit> findByCompanyId(Long companyId);
}
