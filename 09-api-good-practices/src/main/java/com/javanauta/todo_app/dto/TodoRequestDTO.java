package com.javanauta.todo_app.dto;

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

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private LocalDateTime dueDate;
}
