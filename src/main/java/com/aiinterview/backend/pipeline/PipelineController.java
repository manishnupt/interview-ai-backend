package com.aiinterview.backend.pipeline;

import com.aiinterview.backend.auth.AuthenticatedUser;
import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineOrchestrator orchestrator;
    private final CandidateRepository candidateRepository;
    private final AiServiceClient aiServiceClient;

    /**
     * Manually triggers screening batch.
     * COMPANY_ADMIN only.
     */
    @PostMapping("/run-screening")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runScreening() {
        AuthenticatedUser user = SecurityUtils.getCurrentUser();
        System.out.println("[Pipeline] Manual screening triggered by: "
            + user.email());

        CompletableFuture.runAsync(() ->
            orchestrator.runScreeningBatch());

        return ResponseEntity.ok(ApiResponse.ok(
            "Screening batch started",
            Map.of(
                "status", "started",
                "triggeredAt", LocalDateTime.now().toString(),
                "triggeredBy", user.email()
            )
        ));
    }

    /**
     * Manually triggers interview for a specific candidate.
     * COMPANY_ADMIN only.
     */
    @PostMapping("/trigger-interview/{candidateId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerInterview(
        @PathVariable Long candidateId
    ) {
        AuthenticatedUser user = SecurityUtils.getCurrentUser();

        Candidate candidate = candidateRepository
            .findByIdAndCompanyId(candidateId, user.companyId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Candidate not found"));

        if (candidate.getStatus() != CandidateStatus.SHORTLISTED) {
            throw new BusinessException(
                "Candidate must be SHORTLISTED to trigger an interview. "
                + "Current status: " + candidate.getStatus());
        }

        orchestrator.triggerInterview(candidate);

        return ResponseEntity.ok(ApiResponse.ok(
            "Interview triggered",
            Map.of(
                "candidateId", candidateId,
                "candidateName", candidate.getName(),
                "status", "initiated"
            )
        ));
    }

    /**
     * Health check — verifies Python AI service is reachable.
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        boolean pythonHealthy = aiServiceClient.isHealthy();
        return ResponseEntity.ok(ApiResponse.ok(
            Map.of(
                "springBoot", "ok",
                "pythonAiService", pythonHealthy ? "ok" : "unreachable",
                "checkedAt", LocalDateTime.now().toString()
            )
        ));
    }
}
