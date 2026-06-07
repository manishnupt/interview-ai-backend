package com.aiinterview.backend.magiclink;

import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.candidates.InterviewReport;
import com.aiinterview.backend.candidates.InterviewReportRepository;
import com.aiinterview.backend.candidates.ScreeningResult;
import com.aiinterview.backend.candidates.ScreeningResultRepository;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.notifications.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MagicLinkService {

    private final MagicLinkRepository magicLinkRepository;
    private final CandidateRepository candidateRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void generateAndSend(Long candidateId) {
        Candidate candidate = candidateRepository
            .findById(candidateId)
            .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        String token = UUID.randomUUID().toString()
            + UUID.randomUUID().toString().replace("-", "");

        MagicLink link = new MagicLink();
        link.setToken(token);
        link.setCandidateId(candidateId);
        link.setExpiresAt(LocalDateTime.now().plusHours(48));
        link.setUsed(false);
        magicLinkRepository.save(link);

        String url = frontendUrl + "/status/" + token;
        emailService.sendMagicLink(candidate.getEmail(), candidate.getName(), url);

        System.out.println("[MagicLink] Generated for: "
            + candidate.getName() + " | URL: " + url);
    }

    public MagicLinkStatusResponse getStatus(String token) {
        MagicLink link = magicLinkRepository
            .findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired link"));

        if (link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("This link has expired. Please request a new one.", 410);
        }

        Candidate candidate = candidateRepository
            .findById(link.getCandidateId())
            .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        ScreeningResult sr = screeningResultRepository
            .findByCandidateId(candidate.getId())
            .orElse(null);

        InterviewReport ir = interviewReportRepository
            .findByCandidateId(candidate.getId())
            .orElse(null);

        return MagicLinkStatusResponse.builder()
            .candidateName(candidate.getName())
            .jobId(candidate.getJobId())
            .status(candidate.getStatus().name())
            .statusLabel(getStatusLabel(candidate.getStatus()))
            .statusDescription(getStatusDescription(candidate.getStatus()))
            .screeningScore(sr != null ? sr.getScore() : null)
            .interviewScore(ir != null ? ir.getScore() : null)
            .recommendation(ir != null ? ir.getRecommendation() : null)
            .appliedAt(candidate.getAppliedAt())
            .build();
    }

    private String getStatusLabel(CandidateStatus status) {
        return switch (status) {
            case APPLIED     -> "Under review";
            case SCREENING   -> "Being screened";
            case SHORTLISTED -> "Shortlisted";
            case INTERVIEWED -> "Interview complete";
            case HR_REVIEW   -> "Under consideration";
            case OFFERED     -> "Offer extended";
            case REJECTED    -> "Not selected";
        };
    }

    private String getStatusDescription(CandidateStatus status) {
        return switch (status) {
            case APPLIED     -> "Your application is being reviewed by our team.";
            case SCREENING   -> "Your resume is being screened against the role requirements.";
            case SHORTLISTED -> "Great news — you've been shortlisted! Expect a call soon.";
            case INTERVIEWED -> "Your interview is complete. The team is reviewing your results.";
            case HR_REVIEW   -> "You're in the final review stage. The hiring team will be in touch.";
            case OFFERED     -> "Congratulations! The team will reach out with offer details.";
            case REJECTED    -> "Thank you for applying. We've moved forward with other candidates.";
        };
    }
}
