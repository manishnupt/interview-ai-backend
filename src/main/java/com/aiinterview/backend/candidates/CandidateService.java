package com.aiinterview.backend.candidates;

import com.aiinterview.backend.auth.User;
import com.aiinterview.backend.auth.UserRepository;
import com.aiinterview.backend.candidates.dto.*;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.files.S3FileService;
import com.aiinterview.backend.jobs.Job;
import com.aiinterview.backend.jobs.JobRepository;
import com.aiinterview.backend.jobs.JobStatus;
import com.aiinterview.backend.notifications.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final HrNoteRepository hrNoteRepository;
    private final UserRepository userRepository;
    private final S3FileService s3FileService;
    private final EmailService emailService;

    @Transactional
    public CandidateResponse apply(String jobSlug, ApplyRequest req, MultipartFile resumeFile) {
        Job job = jobRepository.findBySlugAndStatus(jobSlug, JobStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        "This position is no longer accepting applications"));

        if (candidateRepository.existsByEmailAndJobId(req.getEmail(), job.getId())) {
            throw new BusinessException("You have already applied for this position");
        }

        if (resumeFile != null && !resumeFile.isEmpty()) {
            s3FileService.validateFile(resumeFile);
        }

        Candidate candidate = new Candidate();
        candidate.setCompanyId(job.getCompanyId());
        candidate.setJobId(job.getId());
        candidate.setName(req.getName());
        candidate.setEmail(req.getEmail());
        candidate.setPhone(req.getPhone());
        candidate.setStatus(CandidateStatus.APPLIED);
        candidate.setAppliedAt(LocalDateTime.now());
        candidate = candidateRepository.save(candidate);

        if (resumeFile != null && !resumeFile.isEmpty()) {
            try {
                String s3Key = s3FileService.uploadResume(resumeFile, candidate.getId(), job.getId());
                candidate.setResumeS3Key(s3Key);
                candidate.setResumeUrl(s3FileService.generatePresignedUrl(s3Key));
                candidate = candidateRepository.save(candidate);
            } catch (BusinessException e) {
                System.out.println("[Candidate] Resume upload failed: "
                        + e.getMessage() + " — candidate saved without resume");
            }
        }

        emailService.sendApplicationConfirmation(req.getEmail(), req.getName(), job.getTitle());

        return buildCandidateResponse(candidate, job.getTitle());
    }

    public List<CandidateSummaryResponse> getCandidatesByJob(Long jobId, Long companyId) {
        jobRepository.findByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        return candidateRepository.findAllByJobIdAndCompanyId(jobId, companyId)
                .stream()
                .sorted(Comparator.comparing(Candidate::getAppliedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> buildSummaryResponse(c, null))
                .toList();
    }

    public List<CandidateSummaryResponse> getCandidatesByStatus(Long companyId, String statusStr) {
        CandidateStatus status;
        try {
            status = CandidateStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + statusStr);
        }

        return candidateRepository.findAllByCompanyIdAndStatus(companyId, status)
                .stream()
                .sorted(Comparator.comparing(Candidate::getAppliedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> buildSummaryResponse(c, null))
                .toList();
    }

    public CandidateResponse getCandidate(Long candidateId, Long companyId) {
        Candidate candidate = candidateRepository.findByIdAndCompanyId(candidateId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        if (candidate.getResumeS3Key() != null) {
            candidate.setResumeUrl(s3FileService.generatePresignedUrl(candidate.getResumeS3Key()));
        }

        String jobTitle = jobRepository.findById(candidate.getJobId())
                .map(Job::getTitle)
                .orElse(null);

        return buildCandidateResponse(candidate, jobTitle);
    }

    @Transactional
    public CandidateResponse updateStatus(Long candidateId, Long companyId, CandidateStatus newStatus) {
        Candidate candidate = candidateRepository.findByIdAndCompanyId(candidateId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        candidate.setStatus(newStatus);
        candidate = candidateRepository.save(candidate);

        String jobTitle = jobRepository.findById(candidate.getJobId())
                .map(Job::getTitle)
                .orElse(null);

        return buildCandidateResponse(candidate, jobTitle);
    }

    @Transactional
    public void addNote(Long candidateId, Long companyId, Long authorId, String content) {
        candidateRepository.findByIdAndCompanyId(candidateId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        HrNote note = new HrNote();
        note.setCandidateId(candidateId);
        note.setCompanyId(companyId);
        note.setAuthorId(authorId);
        note.setContent(content);
        hrNoteRepository.save(note);
    }

    public List<HrNoteResponse> getNotes(Long candidateId, Long companyId) {
        candidateRepository.findByIdAndCompanyId(candidateId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        return hrNoteRepository.findAllByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(note -> {
                    String authorName = note.getAuthorId() == null ? "Unknown"
                            : userRepository.findById(note.getAuthorId())
                                    .map(User::getName)
                                    .orElse("Unknown");
                    return HrNoteResponse.builder()
                            .id(note.getId())
                            .content(note.getContent())
                            .authorName(authorName)
                            .createdAt(note.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private CandidateResponse buildCandidateResponse(Candidate candidate, String jobTitle) {
        ScreeningResultDto screeningDto = screeningResultRepository
                .findByCandidateId(candidate.getId())
                .map(s -> ScreeningResultDto.builder()
                        .score(s.getScore())
                        .matchPercentage(s.getMatchPercentage())
                        .fit(s.isFit())
                        .fitReasons(s.getFitReasons())
                        .concerns(s.getConcerns())
                        .missingSkills(s.getMissingSkills())
                        .screenedAt(s.getScreenedAt())
                        .build())
                .orElse(null);

        InterviewReportDto interviewDto = interviewReportRepository
                .findByCandidateId(candidate.getId())
                .map(r -> InterviewReportDto.builder()
                        .score(r.getScore())
                        .strengths(r.getStrengths())
                        .weaknesses(r.getWeaknesses())
                        .recommendation(r.getRecommendation())
                        .summary(r.getSummary())
                        .fullTranscript(r.getFullTranscript())
                        .interviewedAt(r.getInterviewedAt())
                        .build())
                .orElse(null);

        List<HrNoteResponse> notes = getNotes(candidate.getId(), candidate.getCompanyId());

        return CandidateResponse.builder()
                .id(candidate.getId())
                .jobId(candidate.getJobId())
                .jobTitle(jobTitle)
                .name(candidate.getName())
                .email(candidate.getEmail())
                .phone(candidate.getPhone())
                .resumeUrl(candidate.getResumeUrl())
                .status(candidate.getStatus().name())
                .appliedAt(candidate.getAppliedAt())
                .screeningResult(screeningDto)
                .interviewReport(interviewDto)
                .notes(notes)
                .build();
    }

    private CandidateSummaryResponse buildSummaryResponse(Candidate candidate, String jobTitle) {
        Integer screeningScore = screeningResultRepository
                .findByCandidateId(candidate.getId())
                .map(ScreeningResult::getScore)
                .orElse(null);

        InterviewReport report = interviewReportRepository
                .findByCandidateId(candidate.getId())
                .orElse(null);

        Integer interviewScore = report != null ? report.getScore() : null;
        String recommendation = report != null ? report.getRecommendation() : null;

        return CandidateSummaryResponse.builder()
                .id(candidate.getId())
                .name(candidate.getName())
                .email(candidate.getEmail())
                .phone(candidate.getPhone())
                .status(candidate.getStatus().name())
                .jobTitle(jobTitle)
                .screeningScore(screeningScore)
                .interviewScore(interviewScore)
                .recommendation(recommendation)
                .appliedAt(candidate.getAppliedAt())
                .build();
    }
}
