package com.microservices.todo.exception;

public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException(String id) {
        super("Todo not found: " + id);
    }
}
