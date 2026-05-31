package com.aiinterview.backend.jobs;

import com.aiinterview.backend.auth.AuthenticatedUser;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.SecurityUtils;
import com.aiinterview.backend.jobs.dto.CreateJobRequest;
import com.aiinterview.backend.jobs.dto.JobResponse;
import com.aiinterview.backend.jobs.dto.JobSummaryResponse;
import com.aiinterview.backend.jobs.dto.UpdateJobRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @RequestBody @Valid CreateJobRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(jobService.createJob(request, currentUser.companyId(), currentUser.userId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobSummaryResponse>>> listJobs() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.listJobs(currentUser.companyId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable Long id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(id, currentUser.companyId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable Long id,
            @RequestBody @Valid UpdateJobRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.updateJob(id, request, currentUser.companyId())));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobResponse>> publishJob(@PathVariable Long id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.publishJob(id, currentUser.companyId())));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobResponse>> unpublishJob(@PathVariable Long id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.unpublishJob(id, currentUser.companyId())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobResponse>> closeJob(@PathVariable Long id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(jobService.closeJob(id, currentUser.companyId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        jobService.deleteJob(id, currentUser.companyId());
        return ResponseEntity.noContent().build();
    }
}
