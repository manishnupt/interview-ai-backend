package com.aiinterview.backend.auth;

import com.aiinterview.backend.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public RefreshToken createRefreshToken(Long userId, Long companyId) {
        String rawToken = jwtUtil.generateRefreshToken();
        String tokenHash = jwtUtil.hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUserId(userId);
        refreshToken.setCompanyId(companyId);
        refreshToken.setExpiresAt(LocalDateTime.now()
                .plusDays(jwtUtil.getRefreshTokenExpirationDays()));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);

        // Temporarily overwrite hash with raw token so the caller can write it
        // to the cookie — caller must hash again when validating
        refreshToken.setTokenHash(rawToken);
        return refreshToken;
    }

    public RefreshToken validateRefreshToken(String rawToken) {
        String tokenHash = jwtUtil.hashToken(rawToken);

        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", 401));

        if (!token.isValid()) {
            refreshTokenRepository.delete(token);
            throw new BusinessException("Refresh token expired or revoked. Please log in again.", 401);
        }

        return token;
    }

    public void revokeRefreshToken(String rawToken) {
        String tokenHash = jwtUtil.hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    public void revokeAllUserTokens(Long userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(userId);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    public String buildRefreshCookie(String rawToken, int expiryDays) {
        return ResponseCookie.from("refresh_token", rawToken)
                .httpOnly(true)
                .secure(false) // true in production
                .path("/api/auth")
                .maxAge(Duration.ofDays(expiryDays))
                .sameSite("Strict")
                .build()
                .toString();
    }

    public String clearRefreshCookie() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false) // true in production
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build()
                .toString();
    }

    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        System.out.println("[Auth] Cleaned up expired refresh tokens");
    }
}
