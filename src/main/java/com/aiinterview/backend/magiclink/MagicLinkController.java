package com.aiinterview.backend.magiclink;

import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/magic-link")
@RequiredArgsConstructor
public class MagicLinkController {

    private final MagicLinkService magicLinkService;
    private final CandidateRepository candidateRepository;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> send(@RequestParam String email) {
        candidateRepository.findTopByEmailOrderByAppliedAtDesc(email)
            .ifPresent(c -> magicLinkService.generateAndSend(c.getId()));

        return ResponseEntity.ok(ApiResponse.ok(
            "If we found an application for that email, a status link has been sent.",
            null
        ));
    }

    @GetMapping("/status/{token}")
    public ResponseEntity<ApiResponse<MagicLinkStatusResponse>> getStatus(
            @PathVariable String token) {
        MagicLinkStatusResponse response = magicLinkService.getStatus(token);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
