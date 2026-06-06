package com.aiinterview.backend.candidates;

import com.aiinterview.backend.candidates.dto.ApplyRequest;
import com.aiinterview.backend.candidates.dto.CandidateResponse;
import com.aiinterview.backend.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCandidateController {

    private final CandidateService candidateService;

    @PostMapping(
        value = "/apply/{jobSlug}",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<CandidateResponse>> apply(
            @PathVariable String jobSlug,
            @Valid @ModelAttribute ApplyRequest req,
            @RequestParam(value = "resume", required = false) MultipartFile resume
    ) {
        CandidateResponse response = candidateService.apply(jobSlug, req, resume);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted successfully", response));
    }
}
