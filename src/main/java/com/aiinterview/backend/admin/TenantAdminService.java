package com.aiinterview.backend.admin;

import com.aiinterview.backend.admin.dto.*;
import com.aiinterview.backend.auth.Role;
import com.aiinterview.backend.auth.User;
import com.aiinterview.backend.auth.UserRepository;
import com.aiinterview.backend.candidates.Candidate;
import com.aiinterview.backend.candidates.CandidateRepository;
import com.aiinterview.backend.candidates.CandidateStatus;
import com.aiinterview.backend.common.BusinessException;
import com.aiinterview.backend.common.ResourceNotFoundException;
import com.aiinterview.backend.company.Company;
import com.aiinterview.backend.company.CompanyRepository;
import com.aiinterview.backend.company.CompanyStatus;
import com.aiinterview.backend.jobs.Job;
import com.aiinterview.backend.jobs.JobRepository;
import com.aiinterview.backend.jobs.JobStatus;
import com.aiinterview.backend.usage.PlanLimit;
import com.aiinterview.backend.usage.PlanLimitRepository;
import com.aiinterview.backend.usage.UsageDailySummary;
import com.aiinterview.backend.usage.UsageDailySummaryRepository;
import com.aiinterview.backend.usage.UsageRecord;
import com.aiinterview.backend.usage.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final UsageDailySummaryRepository usageDailySummaryRepository;
    private final PlanLimitRepository planLimitRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Lists every tenant with a rolled-up usage summary
     * for the current month. This is the main admin dashboard view.
     */
    public List<TenantListItemDto> listAllTenants() {
        List<Company> companies = companyRepository.findAll();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        return companies.stream()
                .map(company -> buildTenantListItem(company, monthStart))
                .toList();
    }

    private TenantListItemDto buildTenantListItem(Company company, LocalDate monthStart) {
        int totalUsers = userRepository.findAllByCompanyId(company.getId()).size();

        List<Job> jobs = jobRepository.findAllByCompanyId(company.getId());
        int activeJobs = (int) jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.PUBLISHED)
                .count();

        List<UsageDailySummary> monthSummaries = usageDailySummaryRepository
                .findAllByCompanyIdAndSummaryDateBetween(
                        company.getId(), monthStart, LocalDate.now());

        int interviews = monthSummaries.stream()
                .mapToInt(UsageDailySummary::getTotalInterviews).sum();
        int screenings = monthSummaries.stream()
                .mapToInt(UsageDailySummary::getTotalScreenings).sum();
        BigDecimal cost = monthSummaries.stream()
                .map(UsageDailySummary::getTotalCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PlanLimit limit = planLimitRepository.findByCompanyId(company.getId())
                .orElseGet(() -> defaultPlanLimit(company.getId()));

        int usagePct = 0;
        String usageStatus = "ok";
        if (limit.getMonthlyCostCapUsd().compareTo(BigDecimal.ZERO) > 0) {
            usagePct = cost
                    .divide(limit.getMonthlyCostCapUsd(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
            if (usagePct >= 100) usageStatus = "exceeded";
            else if (usagePct >= limit.getAlertThresholdPct()) usageStatus = "warning";
        }

        return TenantListItemDto.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .plan(company.getPlan())
                .isActive(company.isActive())
                .status(company.getStatus().name())
                .createdAt(company.getCreatedAt())
                .totalUsers(totalUsers)
                .activeJobs(activeJobs)
                .interviewsThisMonth(interviews)
                .screeningsThisMonth(screenings)
                .costThisMonthUsd(cost)
                .monthlyInterviewCap(limit.getMonthlyInterviewCap())
                .monthlyCostCapUsd(limit.getMonthlyCostCapUsd())
                .usagePercentage(usagePct)
                .usageStatus(usageStatus)
                .build();
    }

    private PlanLimit defaultPlanLimit(Long companyId) {
        PlanLimit limit = new PlanLimit();
        limit.setCompanyId(companyId);
        return planLimitRepository.save(limit);
    }

    /**
     * Full drill-down for a single tenant — usage history,
     * cost breakdown by vendor, users, jobs, plan limits.
     */
    public TenantDetailDto getTenantDetail(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        List<TenantUserDto> users = userRepository.findAllByCompanyId(companyId).stream()
                .map(u -> TenantUserDto.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .isActive(u.isActive())
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();

        List<TenantJobSummaryDto> jobs = jobRepository.findAllByCompanyId(companyId).stream()
                .map(j -> {
                    List<Candidate> candidates = candidateRepository
                            .findAllByJobIdAndCompanyId(j.getId(), companyId);
                    int applicantCount = candidates.size();
                    int shortlisted = (int) candidates.stream()
                            .filter(c -> c.getStatus() == CandidateStatus.SHORTLISTED)
                            .count();
                    return TenantJobSummaryDto.builder()
                            .id(j.getId())
                            .title(j.getTitle())
                            .status(j.getStatus().name())
                            .applicantCount(applicantCount)
                            .shortlistedCount(shortlisted)
                            .build();
                })
                .toList();

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<UsageDailySummaryDto> usageHistory = usageDailySummaryRepository
                .findAllByCompanyIdAndSummaryDateBetween(companyId, thirtyDaysAgo, LocalDate.now())
                .stream()
                .map(s -> UsageDailySummaryDto.builder()
                        .date(s.getSummaryDate())
                        .totalInterviews(s.getTotalInterviews())
                        .totalScreenings(s.getTotalScreenings())
                        .totalMinutes(s.getTotalMinutes())
                        .totalCostUsd(s.getTotalCostUsd())
                        .build())
                .toList();

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        List<UsageRecord> monthRecords = usageRecordRepository
                .findAllByCompanyIdAndCreatedAtBetween(
                        companyId, monthStart.atStartOfDay(), LocalDateTime.now());

        CostBreakdownDto costBreakdown = CostBreakdownDto.builder()
                .twilioCostUsd(sumField(monthRecords, UsageRecord::getTwilioCostUsd))
                .deepgramCostUsd(sumField(monthRecords, UsageRecord::getDeepgramCostUsd))
                .elevenlabsCostUsd(sumField(monthRecords, UsageRecord::getElevenlabsCostUsd))
                .openaiCostUsd(sumField(monthRecords, UsageRecord::getOpenaiCostUsd))
                .totalCostUsd(sumField(monthRecords, UsageRecord::getTotalCostUsd))
                .build();

        PlanLimit limit = planLimitRepository.findByCompanyId(companyId)
                .orElseGet(() -> defaultPlanLimit(companyId));

        PlanLimitDto planLimitDto = PlanLimitDto.builder()
                .monthlyInterviewCap(limit.getMonthlyInterviewCap())
                .monthlyScreeningCap(limit.getMonthlyScreeningCap())
                .monthlyCostCapUsd(limit.getMonthlyCostCapUsd())
                .alertThresholdPct(limit.getAlertThresholdPct())
                .build();

        return TenantDetailDto.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .plan(company.getPlan())
                .isActive(company.isActive())
                .status(company.getStatus().name())
                .createdAt(company.getCreatedAt())
                .users(users)
                .jobs(jobs)
                .usageHistory(usageHistory)
                .costBreakdown(costBreakdown)
                .planLimit(planLimitDto)
                .build();
    }

    private BigDecimal sumField(List<UsageRecord> records, Function<UsageRecord, BigDecimal> getter) {
        return records.stream()
                .map(getter)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Creates a new tenant — moves the registration logic
     * here from AuthService, now admin-initiated.
     */
    public TenantDetailDto createTenant(CreateTenantRequest req) {
        if (userRepository.existsByEmail(req.getAdminEmail())) {
            throw new BusinessException("Email already in use: " + req.getAdminEmail());
        }

        String slug = generateUniqueSlug(req.getCompanyName());

        Company company = new Company();
        company.setName(req.getCompanyName());
        company.setSlug(slug);
        company.setPlan(req.getPlan());
        company.setActive(true);
        company = companyRepository.save(company);

        User admin = new User();
        admin.setCompanyId(company.getId());
        admin.setName(req.getAdminName());
        admin.setEmail(req.getAdminEmail());
        admin.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        admin.setRole(Role.COMPANY_ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        defaultPlanLimit(company.getId());

        System.out.println("[Admin] Tenant created: " + company.getName()
                + " | slug: " + slug + " | admin: " + req.getAdminEmail());

        return getTenantDetail(company.getId());
    }

    private String generateUniqueSlug(String companyName) {
        String base = companyName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
        String slug = base;
        int suffix = 2;
        while (companyRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + suffix;
            suffix++;
        }
        return slug;
    }

    /**
     * Updates a tenant's plan tier and usage limits.
     */
    public TenantDetailDto updatePlan(Long companyId, UpdatePlanRequest req) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        company.setPlan(req.getPlan());
        companyRepository.save(company);

        PlanLimit limit = planLimitRepository.findByCompanyId(companyId)
                .orElseGet(() -> defaultPlanLimit(companyId));

        if (req.getMonthlyInterviewCap() != null)
            limit.setMonthlyInterviewCap(req.getMonthlyInterviewCap());
        if (req.getMonthlyScreeningCap() != null)
            limit.setMonthlyScreeningCap(req.getMonthlyScreeningCap());
        if (req.getMonthlyCostCapUsd() != null)
            limit.setMonthlyCostCapUsd(req.getMonthlyCostCapUsd());
        if (req.getAlertThresholdPct() != null)
            limit.setAlertThresholdPct(req.getAlertThresholdPct());

        planLimitRepository.save(limit);

        System.out.println("[Admin] Plan updated for company " + companyId + " → " + req.getPlan());

        return getTenantDetail(companyId);
    }

    /**
     * Adds a user directly to a tenant — admin-initiated,
     * skips the self-invite email flow entirely.
     */
    public TenantUserDto addUserToTenant(Long companyId, AddTenantUserRequest req) {
        companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email already in use: " + req.getEmail());
        }

        Role role;
        try {
            role = Role.valueOf(req.getRole());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid role: " + req.getRole());
        }

        if (role == Role.SUPER_ADMIN) {
            throw new BusinessException("Cannot create SUPER_ADMIN users via tenant management");
        }

        User user = new User();
        user.setCompanyId(companyId);
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(role);
        user.setActive(true);
        user = userRepository.save(user);

        System.out.println("[Admin] User added to company "
                + companyId + ": " + req.getEmail() + " (" + role + ")");

        return TenantUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public void activateTenant(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        company.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);
        System.out.println("[Admin] Tenant " + company.getName() + " activated");
    }

    public void deactivateTenant(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        company.setStatus(CompanyStatus.DEACTIVATED);
        companyRepository.save(company);
        System.out.println("[Admin] Tenant " + company.getName() + " deactivated");
    }

    public void archiveTenant(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        company.setStatus(CompanyStatus.ARCHIVED);
        companyRepository.save(company);
        System.out.println("[Admin] Tenant " + company.getName() + " archived");
    }

    public PlatformDashboardDto getPlatformDashboard() {
        List<Company> allCompanies = companyRepository.findAll();

        int active = (int) allCompanies.stream()
                .filter(c -> c.getStatus() == CompanyStatus.ACTIVE).count();
        int deactivated = (int) allCompanies.stream()
                .filter(c -> c.getStatus() == CompanyStatus.DEACTIVATED).count();
        int archived = (int) allCompanies.stream()
                .filter(c -> c.getStatus() == CompanyStatus.ARCHIVED).count();

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();

        List<UsageDailySummary> allMonthSummaries =
                usageDailySummaryRepository.findAllBySummaryDateBetween(monthStart, today);

        int interviews = allMonthSummaries.stream()
                .mapToInt(UsageDailySummary::getTotalInterviews).sum();
        int screenings = allMonthSummaries.stream()
                .mapToInt(UsageDailySummary::getTotalScreenings).sum();
        BigDecimal totalCost = allMonthSummaries.stream()
                .map(UsageDailySummary::getTotalCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> costByCompany = allMonthSummaries.stream()
                .collect(Collectors.groupingBy(
                        UsageDailySummary::getCompanyId,
                        Collectors.reducing(BigDecimal.ZERO,
                                UsageDailySummary::getTotalCostUsd, BigDecimal::add)));

        Map<Long, Integer> interviewsByCompany = allMonthSummaries.stream()
                .collect(Collectors.groupingBy(
                        UsageDailySummary::getCompanyId,
                        Collectors.summingInt(UsageDailySummary::getTotalInterviews)));

        List<TopTenantDto> topTenants = costByCompany.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Company c = allCompanies.stream()
                            .filter(co -> co.getId().equals(entry.getKey()))
                            .findFirst().orElse(null);
                    return TopTenantDto.builder()
                            .id(entry.getKey())
                            .name(c != null ? c.getName() : "Unknown")
                            .costThisMonthUsd(entry.getValue())
                            .interviewsThisMonth(
                                    interviewsByCompany.getOrDefault(entry.getKey(), 0))
                            .build();
                })
                .toList();

        LocalDate thirtyDaysAgo = today.minusDays(30);
        List<UsageDailySummary> last30Days =
                usageDailySummaryRepository.findAllBySummaryDateBetween(thirtyDaysAgo, today);

        Map<LocalDate, List<UsageDailySummary>> byDate = last30Days.stream()
                .collect(Collectors.groupingBy(UsageDailySummary::getSummaryDate));

        List<UsageDailySummaryDto> platformHistory = byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<UsageDailySummary> dayRecords = entry.getValue();
                    return UsageDailySummaryDto.builder()
                            .date(entry.getKey())
                            .totalInterviews(dayRecords.stream()
                                    .mapToInt(UsageDailySummary::getTotalInterviews).sum())
                            .totalScreenings(dayRecords.stream()
                                    .mapToInt(UsageDailySummary::getTotalScreenings).sum())
                            .totalMinutes(dayRecords.stream()
                                    .mapToDouble(UsageDailySummary::getTotalMinutes).sum())
                            .totalCostUsd(dayRecords.stream()
                                    .map(UsageDailySummary::getTotalCostUsd)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .build();
                })
                .toList();

        List<TenantListItemDto> allTenantItems = listAllTenants();
        List<TenantAlertDto> nearLimit = allTenantItems.stream()
                .filter(t -> "warning".equals(t.getUsageStatus())
                        || "exceeded".equals(t.getUsageStatus()))
                .map(t -> TenantAlertDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .usagePercentage(t.getUsagePercentage())
                        .usageStatus(t.getUsageStatus())
                        .build())
                .toList();

        return PlatformDashboardDto.builder()
                .totalTenants(allCompanies.size())
                .activeTenants(active)
                .deactivatedTenants(deactivated)
                .archivedTenants(archived)
                .interviewsThisMonth(interviews)
                .screeningsThisMonth(screenings)
                .totalCostThisMonthUsd(totalCost)
                .topTenantsByCost(topTenants)
                .platformUsageHistory(platformHistory)
                .tenantsNearLimit(nearLimit)
                .build();
    }

    public List<UsageRecord> exportUsageRecords(
            Long companyId, LocalDate startDate, LocalDate endDate) {
        companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        return usageRecordRepository.findAllByCompanyIdAndCreatedAtBetween(
                companyId,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());
    }
}
