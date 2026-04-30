package com.javanauta.todo_app.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.javanauta.todo_app.model.User;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recoverToken(request);
        if (token != null) {
            jwtService.validateToken(token).ifPresent(decoded -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        buildPrincipal(decoded), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }

    private User buildPrincipal(DecodedJWT decoded) {
        return User.builder()
                .id(decoded.getClaim("userId").asLong())
                .email(decoded.getSubject())
                .name(decoded.getClaim("name").asString())
                .build();
    }

    private String recoverToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.replace("Bearer ", "");
    }
}
