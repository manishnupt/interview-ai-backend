package com.aiinterview.backend.jobs;

import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.company.Company;
import com.aiinterview.backend.company.CompanyRepository;
import com.aiinterview.backend.jobs.dto.CreateJobRequest;
import com.aiinterview.backend.jobs.dto.JobResponse;
import com.aiinterview.backend.jobs.dto.JobSummaryResponse;
import com.aiinterview.backend.jobs.dto.UpdateJobRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final CandidateRepository candidateRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public JobResponse createJob(CreateJobRequest req, Long companyId, Long createdBy) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));

        String slug = generateUniqueSlug(company.getSlug(), req.getTitle());

        Job job = new Job();
        job.setCompanyId(companyId);
        job.setTitle(req.getTitle());
        job.setDescription(req.getDescription());
        job.setRequiredExperienceYears(req.getRequiredExperienceYears());
        job.setRequiredSkills(req.getRequiredSkills());
        job.setScreeningThreshold(req.getScreeningThreshold() != null ? req.getScreeningThreshold() : 6);
        job.setOpeningsCount(req.getOpeningsCount() != null ? req.getOpeningsCount() : 1);
        job.setStatus(JobStatus.DRAFT);
        job.setSlug(slug);
        job.setCreatedBy(createdBy);
        job = jobRepository.save(job);

        System.out.println("[Jobs] Created job: " + req.getTitle() + " | slug: " + slug);
        return toJobResponse(job, 0, 0);
    }

    @Transactional
    public JobResponse publishJob(Long jobId, Long companyId) {
        Job job = findOwned(jobId, companyId);
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new BusinessException("Job is already published");
        }
        job.setStatus(JobStatus.PUBLISHED);
        return toJobResponseWithCounts(jobRepository.save(job));
    }

    @Transactional
    public JobResponse unpublishJob(Long jobId, Long companyId) {
        Job job = findOwned(jobId, companyId);
        job.setStatus(JobStatus.DRAFT);
        return toJobResponseWithCounts(jobRepository.save(job));
    }

    @Transactional
    public JobResponse closeJob(Long jobId, Long companyId) {
        Job job = findOwned(jobId, companyId);
        job.setStatus(JobStatus.CLOSED);
        return toJobResponseWithCounts(jobRepository.save(job));
    }

    @Transactional
    public JobResponse updateJob(Long jobId, UpdateJobRequest req, Long companyId) {
        Job job = findOwned(jobId, companyId);

        if (req.getTitle() != null) job.setTitle(req.getTitle());
        if (req.getDescription() != null) job.setDescription(req.getDescription());
        if (req.getRequiredExperienceYears() != null) job.setRequiredExperienceYears(req.getRequiredExperienceYears());
        if (req.getRequiredSkills() != null) job.setRequiredSkills(req.getRequiredSkills());
        if (req.getScreeningThreshold() != null) job.setScreeningThreshold(req.getScreeningThreshold());
        if (req.getOpeningsCount() != null) job.setOpeningsCount(req.getOpeningsCount());

        return toJobResponseWithCounts(jobRepository.save(job));
    }

    public JobResponse getJob(Long jobId, Long companyId) {
        Job job = findOwned(jobId, companyId);
        return toJobResponseWithCounts(job);
    }

    public List<JobSummaryResponse> listJobs(Long companyId) {
        return jobRepository.findAllByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(Job::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toJobSummaryResponse)
                .toList();
    }

    @Transactional
    public void deleteJob(Long jobId, Long companyId) {
        Job job = findOwned(jobId, companyId);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new BusinessException("Cannot delete a published job. Unpublish it first.");
        }
        jobRepository.delete(job);
    }

    public JobResponse getJobBySlug(String slug) {
        Job job = jobRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + slug));
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new EntityNotFoundException("Job not found: " + slug);
        }
        return toJobResponseWithCounts(job);
    }

    public String buildApplyUrl(String slug) {
        return frontendUrl + "/apply/" + slug;
    }

    public String buildEmbedCode(String slug) {
        return """
                <!-- AI Interview Apply Form -->
                <iframe
                  src="%s/apply/%s"
                  width="100%%"
                  height="600"
                  frameborder="0"
                  style="border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                </iframe>
                """.formatted(frontendUrl, slug);
    }

    private Job findOwned(Long jobId, Long companyId) {
        return jobRepository.findByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
    }

    private JobResponse toJobResponseWithCounts(Job job) {
        int total = candidateRepository.findAllByJobId(job.getId()).size();
        int shortlisted = candidateRepository
                .findAllByStatusAndCompanyId(CandidateStatus.SHORTLISTED, job.getCompanyId())
                .stream()
                .filter(c -> c.getJobId().equals(job.getId()))
                .toList()
                .size();
        return toJobResponse(job, total, shortlisted);
    }

    private JobResponse toJobResponse(Job job, int applicantCount, int shortlistedCount) {
        return JobResponse.builder()
                .id(job.getId())
                .companyId(job.getCompanyId())
                .title(job.getTitle())
                .description(job.getDescription())
                .requiredExperienceYears(job.getRequiredExperienceYears())
                .requiredSkills(job.getRequiredSkills())
                .screeningThreshold(job.getScreeningThreshold())
                .status(job.getStatus().name())
                .slug(job.getSlug())
                .openingsCount(job.getOpeningsCount())
                .embedCode(buildEmbedCode(job.getSlug()))
                .applyUrl(buildApplyUrl(job.getSlug()))
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .applicantCount(applicantCount)
                .shortlistedCount(shortlistedCount)
                .build();
    }

    private JobSummaryResponse toJobSummaryResponse(Job job) {
        int total = candidateRepository.findAllByJobId(job.getId()).size();
        int shortlisted = candidateRepository
                .findAllByStatusAndCompanyId(CandidateStatus.SHORTLISTED, job.getCompanyId())
                .stream()
                .filter(c -> c.getJobId().equals(job.getId()))
                .toList()
                .size();
        return JobSummaryResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .status(job.getStatus().name())
                .slug(job.getSlug())
                .openingsCount(job.getOpeningsCount())
                .screeningThreshold(job.getScreeningThreshold())
                .applicantCount(total)
                .shortlistedCount(shortlisted)
                .createdAt(job.getCreatedAt())
                .build();
    }

    private String generateUniqueSlug(String companySlug, String title) {
        String titleSlug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-");
        String base = companySlug + "-" + titleSlug;

        if (jobRepository.findBySlug(base).isEmpty()) {
            return base;
        }
        int suffix = 2;
        while (jobRepository.findBySlug(base + "-" + suffix).isPresent()) {
            suffix++;
        }
        return base + "-" + suffix;
    }
}
