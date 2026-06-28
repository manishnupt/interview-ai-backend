package com.aiinterview.backend.admin;

import com.aiinterview.backend.admin.dto.*;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.usage.UsageRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTenantController {

    private final TenantAdminService tenantAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantListItemDto>>> listTenants() {
        return ResponseEntity.ok(ApiResponse.ok(tenantAdminService.listAllTenants()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantDetailDto>> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tenantAdminService.getTenantDetail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantDetailDto>> createTenant(
            @RequestBody @Valid CreateTenantRequest req) {
        TenantDetailDto created = tenantAdminService.createTenant(req);
        return ResponseEntity.status(201).body(ApiResponse.ok("Tenant created successfully", created));
    }

    @PutMapping("/{id}/plan")
    public ResponseEntity<ApiResponse<TenantDetailDto>> updatePlan(
            @PathVariable Long id,
            @RequestBody @Valid UpdatePlanRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Plan updated", tenantAdminService.updatePlan(id, req)));
    }

    @PostMapping("/{id}/users")
    public ResponseEntity<ApiResponse<TenantUserDto>> addUser(
            @PathVariable Long id,
            @RequestBody @Valid AddTenantUserRequest req) {
        TenantUserDto user = tenantAdminService.addUserToTenant(id, req);
        return ResponseEntity.status(201).body(ApiResponse.ok("User added to tenant", user));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        tenantAdminService.activateTenant(id);
        return ResponseEntity.ok(ApiResponse.ok("Tenant activated", null));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        tenantAdminService.deactivateTenant(id);
        return ResponseEntity.ok(ApiResponse.ok("Tenant deactivated", null));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable Long id) {
        tenantAdminService.archiveTenant(id);
        return ResponseEntity.ok(ApiResponse.ok("Tenant archived", null));
    }

    @GetMapping("/{id}/usage/export")
    public ResponseEntity<?> exportUsage(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start, end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid date format. Use YYYY-MM-DD."));
        }

        if (end.isBefore(start)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("endDate must not be before startDate"));
        }

        List<UsageRecord> records = tenantAdminService.exportUsageRecords(id, start, end);

        StringBuilder csv = new StringBuilder();
        csv.append("id,candidateId,usageType,durationSeconds,")
                .append("ttsCharacters,sttWords,llmInputTokens,")
                .append("llmOutputTokens,outcome,totalCostUsd,createdAt\n");

        for (UsageRecord r : records) {
            csv.append(r.getId()).append(",")
                    .append(r.getCandidateId()).append(",")
                    .append(r.getUsageType()).append(",")
                    .append(r.getDurationSeconds()).append(",")
                    .append(r.getTtsCharacters()).append(",")
                    .append(r.getSttWords()).append(",")
                    .append(r.getLlmInputTokens()).append(",")
                    .append(r.getLlmOutputTokens()).append(",")
                    .append(r.getOutcome()).append(",")
                    .append(r.getTotalCostUsd()).append(",")
                    .append(r.getCreatedAt()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"usage-export-" + id + ".csv\"")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }
}
