package com.microservices.todo.dto.response;

import com.microservices.todo.infrastructure.entity.Priority;

import java.time.LocalDateTime;

public record TodoResponseDTO(
        String id,
        String title,
        String description,
        boolean completed,
        Priority priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
