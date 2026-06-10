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

import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final AiServiceClient aiServiceClient;
    private final EmailService emailService;
    private final S3FileService s3FileService;
    @Qualifier("screeningExecutor")
    private final Executor screeningExecutor;
    @Qualifier("interviewSemaphore")
    private final Semaphore interviewSemaphore;

    /**
     * Screens a single candidate against their job description.
     * Called by the scheduler for every APPLIED candidate.
     * Stores the result and updates candidate status.
     */
    public void screenCandidate(Candidate candidate) {
        String thread = Thread.currentThread().getName();
        System.out.println("[" + thread + "] [Screener] Starting: "
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
                System.out.println("[" + thread + "] [Screener] Score: "
                    + result.getScore() + "/10"
                    + " | Match: " + result.getMatchPercentage() + "%"
                    + " | Fit: true → SHORTLISTED: " + candidate.getName());
            } else {
                candidate.setStatus(CandidateStatus.REJECTED);
                candidateRepository.save(candidate);
                emailService.sendRejectionEmail(
                    candidate.getEmail(), candidate.getName());
                System.out.println("[" + thread + "] [Screener] Score: "
                    + result.getScore() + "/10"
                    + " | Match: " + result.getMatchPercentage() + "%"
                    + " | Fit: false → REJECTED: " + candidate.getName());
            }

        } catch (Exception e) {
            System.out.println("[" + thread + "] [Screener] FAILED for "
                + candidate.getName() + ": " + e.getMessage());
            candidate.setStatus(CandidateStatus.APPLIED);
            candidateRepository.save(candidate);
        }
    }

    /**
     * Triggers an AI voice interview for a shortlisted candidate.
     * Returns immediately — interview runs async in Python.
     * Report comes back via /api/callbacks/interview-complete.
     * Semaphore caps concurrent outbound Twilio calls.
     */
    public void triggerInterview(Candidate candidate) {
        System.out.println("[Pipeline] Requesting interview slot for: "
            + candidate.getName());

        boolean acquired = false;
        try {
            acquired = interviewSemaphore
                .tryAcquire(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!acquired) {
                System.out.println("[Pipeline] No interview slots available for "
                    + candidate.getName()
                    + " — will retry next scheduler run");
                return;
            }

            System.out.println("[Pipeline] Interview slot acquired for: "
                + candidate.getName()
                + " | Available slots: "
                + interviewSemaphore.availablePermits());

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
                candidate.setStatus(CandidateStatus.HR_REVIEW);
                candidateRepository.save(candidate);
                System.out.println("[Pipeline] Interview call initiated: "
                    + response.getCallSid()
                    + " | Remaining slots: "
                    + interviewSemaphore.availablePermits());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Pipeline] Interview trigger interrupted for: "
                + candidate.getName());
        } catch (Exception e) {
            System.out.println("[Pipeline] Interview trigger failed for "
                + candidate.getName() + ": " + e.getMessage());
        } finally {
            // Release after call is INITIATED, not after it ends.
            // The Python service runs the call async; completion
            // arrives via /api/callbacks/interview-complete.
            if (acquired) {
                interviewSemaphore.release();
                System.out.println("[Pipeline] Interview slot released for: "
                    + candidate.getName());
            }
        }
    }

    /**
     * Runs the full screening batch.
     * Called by scheduler every 30 minutes.
     * Processes all APPLIED candidates across all companies in parallel.
     * Uses screeningExecutor directly to avoid @Async self-invocation bypass.
     */
    public void runScreeningBatch() {
        System.out.println("[Pipeline] === Starting parallel screening batch ===");
        long startTime = System.currentTimeMillis();

        List<Candidate> pending = candidateRepository
            .findAllByStatus(CandidateStatus.APPLIED);

        if (pending.isEmpty()) {
            System.out.println("[Pipeline] No candidates to screen");
            return;
        }

        System.out.println("[Pipeline] Screening " + pending.size()
            + " candidates in parallel");

        List<CompletableFuture<ScreeningResult>> futures = pending.stream()
            .map(candidate -> CompletableFuture.supplyAsync(
                () -> {
                    screenCandidate(candidate);
                    return screeningResultRepository
                        .findByCandidateId(candidate.getId())
                        .orElse(null);
                },
                screeningExecutor
            ))
            .toList();

        CompletableFuture<Void> allOf =
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allOf.get(5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            System.out.println("[Pipeline] Batch timed out after 5 minutes");
        } catch (Exception e) {
            System.out.println("[Pipeline] Batch error: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        long completed = futures.stream()
            .filter(f -> f.isDone() && !f.isCompletedExceptionally())
            .count();
        long failed = futures.stream()
            .filter(CompletableFuture::isCompletedExceptionally)
            .count();

        long shortlisted = pending.stream()
            .map(c -> candidateRepository.findById(c.getId()).orElse(c))
            .filter(c -> c.getStatus() == CandidateStatus.SHORTLISTED)
            .count();
        long rejected = pending.stream()
            .map(c -> candidateRepository.findById(c.getId()).orElse(c))
            .filter(c -> c.getStatus() == CandidateStatus.REJECTED)
            .count();

        System.out.println("[Pipeline] === Batch complete ===");
        System.out.println("[Pipeline] Total: " + pending.size()
            + " | Shortlisted: " + shortlisted
            + " | Rejected: " + rejected
            + " | Errors: " + failed
            + " | Time: " + elapsed + "ms"
            + " (avg " + (elapsed / Math.max(1, pending.size())) + "ms/candidate)");
    }
}
