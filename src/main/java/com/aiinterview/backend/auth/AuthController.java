package com.aiinterview.backend.auth;

import com.aiinterview.backend.auth.dto.*;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody @Valid RegisterCompanyRequest request) {
        Map<String, Object> result = authService.registerCompany(request);
        AuthResponse authResponse = (AuthResponse) result.get("authResponse");
        String cookie = (String) result.get("cookie");
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.ok(authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request) {
        Map<String, Object> result = authService.login(request);
        AuthResponse authResponse = (AuthResponse) result.get("authResponse");
        String cookie = (String) result.get("cookie");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.ok(authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("No refresh token"));
        }
        Map<String, Object> result = authService.refresh(refreshToken);
        AuthResponse authResponse = (AuthResponse) result.get("authResponse");
        String cookie = (String) result.get("cookie");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.ok(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        String clearCookie = refreshTokenService.clearRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie)
                .body(ApiResponse.ok("Logged out successfully", null));
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
        Map<String, Object> result = authService.acceptInvite(request);
        AuthResponse authResponse = (AuthResponse) result.get("authResponse");
        String cookie = (String) result.get("cookie");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.ok(authResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(currentUser.userId())));
    }
}
