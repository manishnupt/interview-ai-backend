package com.aiinterview.backend.candidates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrNoteRepository extends JpaRepository<HrNote, Long> {

    List<HrNote> findAllByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
