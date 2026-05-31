package com.aiinterview.backend.auth;

public record AuthenticatedUser(
        Long userId,
        Long companyId,
        String role,
        String email
) {}
