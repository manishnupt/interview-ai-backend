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
import com.aiinterview.backend.usage.UsageRecord;
import com.aiinterview.backend.usage.UsageRecordRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final CandidateRepository candidateRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UsageRecordRepository usageRecordRepository;

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
        private Map<String, Object> usageMetrics;  // flexible map, Python field names vary
    }

    @PostMapping("/interview-complete")
    public ResponseEntity<ApiResponse<Void>> interviewComplete(
            @RequestBody InterviewCallbackRequest req
    ) {
        System.out.println("[Callback] Received interview callback for candidate: "
            + req.getCandidateId() + " | status: " + req.getStatus());

        // Save usage data first, regardless of outcome
        saveUsageRecord(req);

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

    private void saveUsageRecord(InterviewCallbackRequest req) {
        if (req.getUsageMetrics() == null) {
            System.out.println("[Usage] No usage metrics in callback for candidate: "
                + req.getCandidateId());
            return;
        }

        try {
            Map<String, Object> m = req.getUsageMetrics();

            UsageRecord record = new UsageRecord();
            record.setCompanyId(req.getCompanyId());
            record.setJobId(req.getJobId());
            record.setCandidateId(req.getCandidateId());
            record.setCallSid(req.getCallSid());
            record.setUsageType((String) m.getOrDefault("type", "interview"));
            record.setRateCardVersion((String) m.get("rateCardVersion"));
            record.setDurationSeconds(toDouble(m.get("durationSeconds")));
            record.setTtsCharacters(toInt(m.get("ttsCharacters")));
            record.setSttWords(toInt(m.get("sttWords")));
            record.setLlmInputTokens(toInt(m.get("llmInputTokens")));
            record.setLlmOutputTokens(toInt(m.get("llmOutputTokens")));
            record.setGptCallCount(toInt(m.get("gptCallCount")));
            record.setQuestionsAsked(toInt(m.get("questionsAsked")));
            record.setOutcome((String) m.getOrDefault("outcome", req.getStatus()));
            record.setTwilioCostUsd(toBigDecimal(m.get("twilioCostUsd")));
            record.setDeepgramCostUsd(toBigDecimal(m.get("deepgramCostUsd")));
            record.setElevenlabsCostUsd(toBigDecimal(m.get("elevenlabsCostUsd")));
            record.setOpenaiCostUsd(toBigDecimal(m.get("openaiCostUsd")));
            record.setTotalCostUsd(toBigDecimal(m.get("totalCostUsd")));

            usageRecordRepository.save(record);

            System.out.println("[Usage] Saved record for candidate "
                + req.getCandidateId() + " | cost: $" + record.getTotalCostUsd());

        } catch (Exception e) {
            // Usage tracking failures must NEVER affect interview report processing
            System.out.println("[Usage] Failed to save usage record: " + e.getMessage());
        }
    }

    private Double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0.0;
    }

    private Integer toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        return BigDecimal.ZERO;
    }

    private void notifyHrTeam(Long companyId, String candidateName, int score) {
        List<User> hrUsers = userRepository.findAllByCompanyId(companyId);
        for (User user : hrUsers) {
            emailService.sendInterviewCompleteToHR(user.getEmail(), candidateName, score);
        }
    }
}
