package com.aiinterview.backend.usage;

import com.aiinterview.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageAggregationService aggregationService;
    private final UsageRecordRepository usageRecordRepository;
    private final UsageDailySummaryRepository usageDailySummaryRepository;

    @PostMapping("/aggregate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> triggerAggregation(
            @RequestParam(required = false) String date
    ) {
        LocalDate targetDate = date != null
            ? LocalDate.parse(date)
            : LocalDate.now().minusDays(1);

        aggregationService.aggregateForDate(targetDate);

        return ResponseEntity.ok(ApiResponse.ok(
            "Aggregation triggered for: " + targetDate, null));
    }

    @GetMapping("/raw")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UsageRecord>>> getRawRecords(
            @RequestParam Long companyId
    ) {
        List<UsageRecord> records =
            usageRecordRepository.findAllByCompanyId(companyId);
        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UsageDailySummary>>> getSummary(
            @RequestParam Long companyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        LocalDate start = startDate != null
            ? LocalDate.parse(startDate)
            : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null
            ? LocalDate.parse(endDate)
            : LocalDate.now();

        List<UsageDailySummary> summaries =
            usageDailySummaryRepository
                .findAllByCompanyIdAndSummaryDateBetween(companyId, start, end);

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }
}
