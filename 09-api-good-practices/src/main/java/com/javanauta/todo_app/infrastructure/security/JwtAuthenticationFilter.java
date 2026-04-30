package com.javanauta.todo_app.infrastructure.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.javanauta.todo_app.domain.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recoverToken(request);
        if (token != null) {
            jwtService.validateToken(token)
                    .flatMap(this::buildPrincipal)
                    .ifPresent(user -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        filterChain.doFilter(request, response);
    }

    private Optional<User> buildPrincipal(DecodedJWT decoded) {
        Long userId = decoded.getClaim(JwtService.CLAIM_USER_ID).asLong();
        String name = decoded.getClaim(JwtService.CLAIM_NAME).asString();
        if (userId == null || name == null) return Optional.empty();
        return Optional.of(User.builder()
                .id(userId)
                .email(decoded.getSubject())
                .name(name)
                .build());
    }

    private String recoverToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.replace("Bearer ", "");
    }
}
