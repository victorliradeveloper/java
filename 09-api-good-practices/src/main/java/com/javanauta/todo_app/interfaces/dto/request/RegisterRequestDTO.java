package com.javanauta.todo_app.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @Schema(description = "Full name", example = "John Doe")
        @NotBlank String name,

        @Schema(description = "Email address", example = "john@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Password (min 6 characters)", example = "secret123")
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password
) {}
