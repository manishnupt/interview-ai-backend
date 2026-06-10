package com.aiinterview.backend.pipeline;

import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.candidates.InterviewReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
public class PipelineScheduler {

    private final PipelineOrchestrator orchestrator;
    private final AiServiceClient aiServiceClient;
    private final CandidateRepository candidateRepository;
    private final InterviewReportRepository interviewReportRepository;
    @Qualifier("screeningExecutor")
    private final Executor screeningExecutor;

    /**
     * Runs resume screening every 30 minutes.
     * Picks up all APPLIED candidates and screens them.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void scheduledScreeningRun() {
        System.out.println("[Scheduler] Triggered screening run at: "
            + LocalDateTime.now());
        try {
            orchestrator.runScreeningBatch();
        } catch (Exception e) {
            System.out.println("[Scheduler] Screening batch failed: "
                + e.getMessage());
        }
    }

    /**
     * Triggers interviews for all SHORTLISTED candidates
     * who do not yet have an interview report.
     * Runs every 15 minutes.
     * Semaphore in triggerInterview() caps concurrent Twilio calls.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void scheduledInterviewTrigger() {
        System.out.println("[Scheduler] Checking shortlisted candidates...");

        List<Candidate> shortlisted = candidateRepository
            .findAllByStatus(CandidateStatus.SHORTLISTED);

        List<Candidate> needsInterview = shortlisted.stream()
            .filter(c -> interviewReportRepository
                .findByCandidateId(c.getId())
                .isEmpty())
            .toList();

        if (needsInterview.isEmpty()) {
            System.out.println("[Scheduler] No candidates need interviews");
            return;
        }

        System.out.println("[Scheduler] Triggering interviews for "
            + needsInterview.size() + " candidates");

        List<CompletableFuture<Void>> futures = needsInterview.stream()
            .map(candidate -> CompletableFuture.runAsync(
                () -> orchestrator.triggerInterview(candidate),
                screeningExecutor
            ))
            .toList();

        CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        ).join();

        System.out.println("[Scheduler] Interview trigger batch complete");
    }
}
