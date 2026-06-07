package com.microservices.todo.dto.request;

import com.microservices.todo.infrastructure.entity.Priority;

public record TodoUpdateDTO(
        String title,
        String description,
        Boolean completed,
        Priority priority
) {}
