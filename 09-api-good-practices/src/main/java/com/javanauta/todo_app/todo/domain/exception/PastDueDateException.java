package com.javanauta.todo_app.todo.domain.exception;

import java.time.LocalDateTime;

public class PastDueDateException extends RuntimeException {

    public PastDueDateException(LocalDateTime dueDate) {
        super("Due date must be in the future, got: " + dueDate);
    }
}
