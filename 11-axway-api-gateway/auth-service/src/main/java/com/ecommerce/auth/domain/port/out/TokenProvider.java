package com.ecommerce.auth.domain.port.out;

import com.ecommerce.auth.domain.model.User;

public interface TokenProvider {
    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    String extractUsername(String token);

    boolean isTokenValid(String token, String expectedUsername);

    long getAccessTokenExpiration();
}
