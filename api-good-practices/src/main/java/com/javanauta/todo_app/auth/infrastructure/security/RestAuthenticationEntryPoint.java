package com.javanauta.todo_app.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javanauta.todo_app.shared.web.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        log.warn("Unauthenticated access to {}: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication required",
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
