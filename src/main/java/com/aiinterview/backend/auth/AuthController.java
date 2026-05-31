package com.aiinterview.backend.auth;

import com.aiinterview.backend.auth.dto.*;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody @Valid RegisterCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerCompany(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> inviteUser(
            @RequestBody @Valid InviteUserRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        authService.inviteUser(request, currentUser.companyId());
        return ResponseEntity.ok(ApiResponse.ok("Invitation sent", null));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<AuthResponse>> acceptInvite(
            @RequestBody @Valid AcceptInviteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.acceptInvite(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(currentUser.userId())));
    }
}
