package com.javanauta.todo_app.todo.domain.exception;

public class TodoLimitExceededException extends RuntimeException {

    public TodoLimitExceededException(long limit) {
        super("Todo limit exceeded. Maximum allowed per user: " + limit);
    }
}
