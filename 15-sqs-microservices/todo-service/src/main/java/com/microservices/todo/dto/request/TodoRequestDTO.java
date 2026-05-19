package com.microservices.todo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TodoRequestDTO(
        @NotBlank String title,
        String description
) {}
