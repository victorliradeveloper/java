package com.ecommerce.auth.domain.port.in.result;

public record AuthResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String email,
        String role
) {}
