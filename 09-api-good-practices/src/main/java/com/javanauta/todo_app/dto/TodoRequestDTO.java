package com.javanauta.todo_app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TodoRequestDTO {

    @Schema(description = "Title of the todo", example = "Buy groceries")
    @NotBlank(message = "Title is required")
    private String title;

    @Schema(description = "Optional description", example = "Milk, eggs, bread")
    private String description;

    @Schema(description = "Due date in ISO 8601 format", example = "2026-05-15T18:00:00")
    private LocalDateTime dueDate;
}
