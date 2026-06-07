package com.aiinterview.backend.pipeline;

import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.candidates.ScreeningResult;
import com.aiinterview.backend.candidates.ScreeningResultRepository;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.files.S3FileService;
import com.aiinterview.backend.jobs.Job;
import com.aiinterview.backend.jobs.JobRepository;
import com.aiinterview.backend.notifications.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final AiServiceClient aiServiceClient;
    private final EmailService emailService;
    private final S3FileService s3FileService;

    /**
     * Screens a single candidate against their job description.
     * Called by the scheduler for every APPLIED candidate.
     * Stores the result and updates candidate status.
     */
    public void screenCandidate(Candidate candidate) {
        System.out.println("[Pipeline] Screening: "
            + candidate.getName()
            + " (id=" + candidate.getId() + ")");

        try {
            Job job = jobRepository
                .findById(candidate.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Job not found: " + candidate.getJobId()));

            // Skip if already screened
            if (screeningResultRepository
                    .findByCandidateId(candidate.getId())
                    .isPresent()) {
                System.out.println("[Pipeline] Already screened, skipping: "
                    + candidate.getName());
                return;
            }

            // Skip if no resume
            if (candidate.getResumeUrl() == null
                    || candidate.getResumeUrl().isBlank()) {
                System.out.println("[Pipeline] No resume for: "
                    + candidate.getName() + " — marking no resume");
                candidate.setStatus(CandidateStatus.REJECTED);
                candidateRepository.save(candidate);
                emailService.sendRejectionEmail(
                    candidate.getEmail(), candidate.getName());
                return;
            }

            // Update status to SCREENING
            candidate.setStatus(CandidateStatus.SCREENING);
            candidateRepository.save(candidate);

            // Call Python screening service
            AiServiceClient.ScreenRequest req =
                new AiServiceClient.ScreenRequest(
                    candidate.getId(),
                    candidate.getResumeUrl(),
                    job.getDescription(),
                    job.getId(),
                    job.getCompanyId()
                );

            AiServiceClient.ScreenResponse result =
                aiServiceClient.screenResume(req);

            if (result == null) {
                throw new BusinessException("Null response from screening service");
            }

            // Save screening result to DB
            ScreeningResult screening = new ScreeningResult();
            screening.setCandidateId(candidate.getId());
            screening.setCompanyId(candidate.getCompanyId());
            screening.setScore(result.getScore());
            screening.setMatchPercentage(result.getMatchPercentage());
            screening.setFit(result.getFit());
            screening.setFitReasons(result.getFitReasons() != null
                ? String.join(" | ", result.getFitReasons()) : "");
            screening.setConcerns(result.getConcerns() != null
                ? String.join(" | ", result.getConcerns()) : "");
            screening.setMissingSkills(result.getMissingSkills() != null
                ? String.join(" | ", result.getMissingSkills()) : "");
            screening.setScreenedAt(LocalDateTime.now());
            screeningResultRepository.save(screening);

            // Decide fit based on job threshold
            boolean passes = result.getScore() >= job.getScreeningThreshold();

            if (passes) {
                candidate.setStatus(CandidateStatus.SHORTLISTED);
                candidateRepository.save(candidate);
                emailService.sendShortlistEmail(
                    candidate.getEmail(), candidate.getName());
                System.out.println("[Pipeline] ✓ SHORTLISTED: "
                    + candidate.getName()
                    + " | Score: " + result.getScore()
                    + "/" + job.getScreeningThreshold()
                    + " | Match: " + result.getMatchPercentage() + "%");
            } else {
                candidate.setStatus(CandidateStatus.REJECTED);
                candidateRepository.save(candidate);
                emailService.sendRejectionEmail(
                    candidate.getEmail(), candidate.getName());
                System.out.println("[Pipeline] ✗ REJECTED: "
                    + candidate.getName()
                    + " | Score: " + result.getScore()
                    + "/" + job.getScreeningThreshold());
            }

        } catch (Exception e) {
            System.out.println("[Pipeline] Screening failed for "
                + candidate.getName() + ": " + e.getMessage());
            candidate.setStatus(CandidateStatus.APPLIED);
            candidateRepository.save(candidate);
        }
    }

    /**
     * Triggers an AI voice interview for a shortlisted candidate.
     * Returns immediately — interview runs async in Python.
     * Report comes back via /api/callbacks/interview-complete.
     */
    public void triggerInterview(Candidate candidate) {
        System.out.println("[Pipeline] Triggering interview for: "
            + candidate.getName());

        try {
            Job job = jobRepository
                .findById(candidate.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Job not found: " + candidate.getJobId()));

            AiServiceClient.InterviewRequest req =
                new AiServiceClient.InterviewRequest(
                    candidate.getId(),
                    candidate.getPhone(),
                    candidate.getName(),
                    candidate.getResumeUrl() != null
                        ? candidate.getResumeUrl() : "",
                    job.getDescription(),
                    job.getId(),
                    job.getCompanyId()
                );

            AiServiceClient.InterviewTriggerResponse response =
                aiServiceClient.triggerInterview(req);

            if (response != null) {
                // Mark as HR_REVIEW temporarily while call is in progress
                candidate.setStatus(CandidateStatus.HR_REVIEW);
                candidateRepository.save(candidate);
                System.out.println("[Pipeline] Interview call initiated: "
                    + response.getCallSid());
            }

        } catch (Exception e) {
            System.out.println("[Pipeline] Interview trigger failed for "
                + candidate.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Runs the full screening batch.
     * Called by scheduler every 30 minutes.
     * Processes all APPLIED candidates across all companies.
     */
    public void runScreeningBatch() {
        System.out.println("[Pipeline] === Starting screening batch ===");

        List<Candidate> pending = candidateRepository
            .findAllByStatus(CandidateStatus.APPLIED);

        System.out.println("[Pipeline] Found " + pending.size()
            + " candidates to screen");

        int screened = 0;
        int shortlisted = 0;
        int rejected = 0;
        int errors = 0;

        for (Candidate candidate : pending) {
            try {
                screenCandidate(candidate);

                // Reload to get updated status
                candidate = candidateRepository
                    .findById(candidate.getId())
                    .orElse(candidate);

                screened++;
                if (candidate.getStatus() == CandidateStatus.SHORTLISTED) {
                    shortlisted++;
                } else if (candidate.getStatus() == CandidateStatus.REJECTED) {
                    rejected++;
                }
            } catch (Exception e) {
                errors++;
                System.out.println("[Pipeline] Error processing candidate "
                    + candidate.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("[Pipeline] === Batch complete ===");
        System.out.println("[Pipeline] Screened: " + screened
            + " | Shortlisted: " + shortlisted
            + " | Rejected: " + rejected
            + " | Errors: " + errors);
    }
}
