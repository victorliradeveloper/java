package com.javanauta.todo_app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javanauta.todo_app.dto.ErrorResponseDTO;
import com.javanauta.todo_app.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.auth.requests-per-minute:10}")
    private int authRequestsPerMinute;

    @Value("${rate-limit.api.requests-per-minute:100}")
    private int apiRequestsPerMinute;

    // Atomic INCR + EXPIRE: sets TTL only on first increment to avoid resetting the window
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return current
            """, Long.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean limited;

        if (isPublicAuthEndpoint(request.getRequestURI())) {
            limited = isRateLimited("auth:" + resolveClientIp(request), authRequestsPerMinute);
        } else {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                limited = isRateLimited("api:" + user.getId(), apiRequestsPerMinute);
            } else {
                limited = false;
            }
        }

        if (limited) {
            writeTooManyRequestsResponse(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String identifier, int limit) {
        long window = System.currentTimeMillis() / 60_000L;
        String key = "rate_limit:" + identifier + ":" + window;
        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), "60");
        return count != null && count > limit;
    }

    private boolean isPublicAuthEndpoint(String path) {
        return path.startsWith("/api/v1/auth/");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequestsResponse(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ErrorResponseDTO.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests. Please try again later.", path)
        );
    }
}
