package com.microservices.todo.dto.response;

import java.time.LocalDateTime;

public record TodoResponseDTO(
        String id,
        String title,
        String description,
        boolean completed,
        LocalDateTime createdAt
) {}
