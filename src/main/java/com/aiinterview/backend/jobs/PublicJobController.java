package com.aiinterview.backend.jobs;

import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.jobs.dto.JobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicJobController {

    private final JobService jobService;

    @GetMapping("/jobs/{slug}")
    public ResponseEntity<ApiResponse<JobResponse>> getJobBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJobBySlug(slug)));
    }
}
