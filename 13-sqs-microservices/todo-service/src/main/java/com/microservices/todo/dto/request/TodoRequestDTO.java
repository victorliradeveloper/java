package com.microservices.todo.dto.request;

import com.microservices.todo.infrastructure.entity.Priority;
import jakarta.validation.constraints.NotBlank;

public record TodoRequestDTO(
        @NotBlank String title,
        String description,
        Priority priority
) {}
