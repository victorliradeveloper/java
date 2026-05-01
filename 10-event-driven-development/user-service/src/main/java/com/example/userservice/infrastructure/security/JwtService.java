package com.example.userservice.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.userservice.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-hours}")
    private int expirationHours;

    public String generateToken(User user) {
        return JWT.create()
                .withIssuer("user-service")
                .withSubject(user.getEmail())
                .withClaim("userId", user.getId().toString())
                .withClaim("name", user.getName())
                .withExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(secret));
    }

    public Optional<DecodedJWT> validateToken(String token) {
        try {
            return Optional.of(
                    JWT.require(Algorithm.HMAC256(secret))
                            .withIssuer("user-service")
                            .build()
                            .verify(token)
            );
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }
}
