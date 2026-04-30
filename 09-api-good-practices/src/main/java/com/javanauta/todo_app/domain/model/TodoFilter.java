package com.javanauta.todo_app.domain.model;

import java.time.LocalDateTime;

public record TodoFilter(
        String title,
        Boolean completed,
        LocalDateTime dueDateFrom,
        LocalDateTime dueDateTo
) {}
