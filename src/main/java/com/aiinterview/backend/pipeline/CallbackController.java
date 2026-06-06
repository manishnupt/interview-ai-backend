package com.aiinterview.backend.pipeline;

import com.aiinterview.backend.auth.User;
import com.aiinterview.backend.auth.UserRepository;
import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.candidates.InterviewReport;
import com.aiinterview.backend.candidates.InterviewReportRepository;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.notifications.EmailService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CandidateRepository candidateRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Data
    @NoArgsConstructor
    static class InterviewCallbackRequest {
        private Long candidateId;
        private Long jobId;
        private Long companyId;
        private String callSid;
        private String status;
        private Integer score;
        private List<String> strengths;
        private List<String> weaknesses;
        private String recommendation;
        private String summary;
        private String fullTranscript;
        private String rawJson;
        private String errorMessage;
    }

    @PostMapping("/interview-complete")
    public ResponseEntity<ApiResponse<Void>> interviewComplete(
            @RequestBody InterviewCallbackRequest req
    ) {
        System.out.println("[Callback] Received interview callback for candidate: "
            + req.getCandidateId() + " | status: " + req.getStatus());

        try {
            Candidate candidate = candidateRepository
                .findById(req.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Candidate not found: " + req.getCandidateId()));

            if ("completed".equals(req.getStatus())) {
                InterviewReport report = new InterviewReport();
                report.setCandidateId(req.getCandidateId());
                report.setCompanyId(req.getCompanyId());
                report.setScore(req.getScore());
                report.setStrengths(String.join(" | ",
                    req.getStrengths() != null ? req.getStrengths() : List.of()));
                report.setWeaknesses(String.join(" | ",
                    req.getWeaknesses() != null ? req.getWeaknesses() : List.of()));
                report.setRecommendation(req.getRecommendation());
                report.setSummary(req.getSummary());
                report.setFullTranscript(req.getFullTranscript());
                report.setRawJson(req.getRawJson());
                report.setInterviewedAt(LocalDateTime.now());
                interviewReportRepository.save(report);

                candidate.setStatus(CandidateStatus.INTERVIEWED);
                candidateRepository.save(candidate);

                notifyHrTeam(req.getCompanyId(), candidate.getName(), req.getScore());

                System.out.println("[Callback] Report saved for: "
                    + candidate.getName() + " | Score: " + req.getScore() + "/10");

            } else if ("timeout".equals(req.getStatus())) {
                candidate.setStatus(CandidateStatus.SHORTLISTED);
                candidateRepository.save(candidate);
                System.out.println("[Callback] Interview timed out for: "
                    + candidate.getName());

            } else if ("error".equals(req.getStatus())) {
                candidate.setStatus(CandidateStatus.SHORTLISTED);
                candidateRepository.save(candidate);
                System.out.println("[Callback] Interview error for: "
                    + candidate.getName() + " | " + req.getErrorMessage());
            }

        } catch (Exception e) {
            System.out.println("[Callback] Error processing callback: " + e.getMessage());
            return ResponseEntity.status(500)
                .body(ApiResponse.error(e.getMessage()));
        }

        return ResponseEntity.ok(ApiResponse.ok("Callback processed", null));
    }

    private void notifyHrTeam(Long companyId, String candidateName, int score) {
        List<User> hrUsers = userRepository.findAllByCompanyId(companyId);
        for (User user : hrUsers) {
            emailService.sendInterviewCompleteToHR(user.getEmail(), candidateName, score);
        }
    }
}
