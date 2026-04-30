package com.javanauta.todo_app.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.javanauta.todo_app.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class JwtService {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_NAME = "name";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-hours:2}")
    private int expirationHours;

    public String generateToken(User user) {
        return JWT.create()
                .withIssuer("todo-app")
                .withSubject(user.getEmail())
                .withClaim(CLAIM_USER_ID, user.getId())
                .withClaim(CLAIM_NAME, user.getName())
                .withExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(secret));
    }

    public Optional<DecodedJWT> validateToken(String token) {
        try {
            return Optional.of(JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("todo-app")
                    .build()
                    .verify(token));
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }
}
