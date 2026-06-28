package com.aiinterview.backend.admin;

import com.aiinterview.backend.admin.dto.PlatformDashboardDto;
import com.aiinterview.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDashboardController {

    private final TenantAdminService tenantAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlatformDashboardDto>> getDashboard() {
        return ResponseEntity.ok(
                ApiResponse.ok(tenantAdminService.getPlatformDashboard()));
    }
}
