package com.javanauta.todo_app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        int status,
        String error,
        Map<String, String> fieldErrors,
        Instant timestamp,
        String path
) {
    public static ErrorResponseDTO of(int status, String error, String path) {
        return new ErrorResponseDTO(status, error, null, Instant.now(), path);
    }

    public static ErrorResponseDTO ofValidation(int status, Map<String, String> fieldErrors, String path) {
        return new ErrorResponseDTO(status, null, fieldErrors, Instant.now(), path);
    }
}
