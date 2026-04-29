package com.javanauta.todo_app.exception;

public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(Long id) {
        super("Todo not found with id: " + id);
    }
}
