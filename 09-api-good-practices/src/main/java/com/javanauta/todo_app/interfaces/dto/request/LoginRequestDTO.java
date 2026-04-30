package com.javanauta.todo_app.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @Schema(description = "Email address", example = "john@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Password", example = "secret123")
        @NotBlank String password
) {}
