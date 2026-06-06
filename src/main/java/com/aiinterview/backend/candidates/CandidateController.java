package com.aiinterview.backend.candidates;

import com.aiinterview.backend.candidates.dto.*;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping("/job/{jobId}")
    public ResponseEntity<ApiResponse<List<CandidateSummaryResponse>>> getCandidatesByJob(
            @PathVariable Long jobId
    ) {
        Long companyId = SecurityUtils.getCurrentUser().companyId();
        return ResponseEntity.ok(
                ApiResponse.ok(candidateService.getCandidatesByJob(jobId, companyId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<CandidateSummaryResponse>>> getCandidatesByStatus(
            @PathVariable String status
    ) {
        Long companyId = SecurityUtils.getCurrentUser().companyId();
        return ResponseEntity.ok(
                ApiResponse.ok(candidateService.getCandidatesByStatus(companyId, status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CandidateResponse>> getCandidate(
            @PathVariable Long id
    ) {
        Long companyId = SecurityUtils.getCurrentUser().companyId();
        return ResponseEntity.ok(
                ApiResponse.ok(candidateService.getCandidate(id, companyId)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam CandidateStatus newStatus
    ) {
        Long companyId = SecurityUtils.getCurrentUser().companyId();
        return ResponseEntity.ok(
                ApiResponse.ok(candidateService.updateStatus(id, companyId, newStatus)));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<Void>> addNote(
            @PathVariable Long id,
            @RequestBody @Valid AddNoteRequest req
    ) {
        var currentUser = SecurityUtils.getCurrentUser();
        candidateService.addNote(id, currentUser.companyId(), currentUser.userId(), req.getContent());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Note added", null));
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<List<HrNoteResponse>>> getNotes(
            @PathVariable Long id
    ) {
        Long companyId = SecurityUtils.getCurrentUser().companyId();
        return ResponseEntity.ok(
                ApiResponse.ok(candidateService.getNotes(id, companyId)));
    }
}
