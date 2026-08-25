package com.javanauta.todo_app.todo.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record TodoRequestDTO(
        @Schema(description = "Title of the todo", example = "Buy groceries")
        @NotBlank(message = "Title is required")
        String title,

        @Schema(description = "Optional description", example = "Milk, eggs, bread")
        String description,

        @Schema(description = "Due date in ISO 8601 format", example = "2026-05-15T18:00:00")
        LocalDateTime dueDate
) {}
