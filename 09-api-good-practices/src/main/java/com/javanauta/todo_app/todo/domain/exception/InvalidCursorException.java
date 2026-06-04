package com.javanauta.todo_app.todo.domain.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(Long cursor) {
        super("Invalid cursor value: " + cursor + " (must be null or non-negative)");
    }
}
