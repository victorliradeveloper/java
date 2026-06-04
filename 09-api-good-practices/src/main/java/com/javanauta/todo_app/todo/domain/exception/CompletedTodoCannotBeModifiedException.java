package com.javanauta.todo_app.todo.domain.exception;

public class CompletedTodoCannotBeModifiedException extends RuntimeException {

    public CompletedTodoCannotBeModifiedException(Long id) {
        super("Todo " + id + " is already completed and cannot be modified");
    }
}
