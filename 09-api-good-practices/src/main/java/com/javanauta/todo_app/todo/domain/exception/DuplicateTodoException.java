package com.javanauta.todo_app.todo.domain.exception;

public class DuplicateTodoException extends RuntimeException {

    public DuplicateTodoException(String title) {
        super("An active todo with title '" + title + "' already exists for this user");
    }
}
