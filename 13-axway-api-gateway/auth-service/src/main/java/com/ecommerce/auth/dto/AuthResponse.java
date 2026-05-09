package com.ecommerce.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String email,
        String role
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, String email, String role) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, email, role);
    }
}
