package com.ecommerce.auth.infrastructure.adapter.in.web.dto;

import com.ecommerce.auth.domain.port.in.result.AuthResult;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String email,
        String role
) {
    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.expiresIn(),
                result.email(),
                result.role()
        );
    }
}
