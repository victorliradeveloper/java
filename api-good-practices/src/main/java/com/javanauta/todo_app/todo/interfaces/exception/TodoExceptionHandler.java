package com.javanauta.todo_app.todo.interfaces.exception;

import com.javanauta.todo_app.shared.web.dto.ErrorResponseDTO;
import com.javanauta.todo_app.todo.domain.exception.CompletedTodoCannotBeModifiedException;
import com.javanauta.todo_app.todo.domain.exception.DuplicateTodoException;
import com.javanauta.todo_app.todo.domain.exception.InvalidCursorException;
import com.javanauta.todo_app.todo.domain.exception.PastDueDateException;
import com.javanauta.todo_app.todo.domain.exception.TodoLimitExceededException;
import com.javanauta.todo_app.todo.domain.exception.TodoNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TodoExceptionHandler {

    @ExceptionHandler(TodoNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(TodoNotFoundException ex, HttpServletRequest request) {
        log.warn("Todo not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDTO.of(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PastDueDateException.class)
    public ResponseEntity<ErrorResponseDTO> handlePastDueDate(PastDueDateException ex, HttpServletRequest request) {
        log.warn("Past due date at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CompletedTodoCannotBeModifiedException.class)
    public ResponseEntity<ErrorResponseDTO> handleCompletedTodoCannotBeModified(CompletedTodoCannotBeModifiedException ex, HttpServletRequest request) {
        log.warn("Attempt to modify completed todo at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TodoLimitExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleTodoLimitExceeded(TodoLimitExceededException ex, HttpServletRequest request) {
        log.warn("Todo limit exceeded at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateTodoException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateTodo(DuplicateTodoException ex, HttpServletRequest request) {
        log.warn("Duplicate todo at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(HttpStatus.CONFLICT.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCursor(InvalidCursorException ex, HttpServletRequest request) {
        log.warn("Invalid cursor at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI()));
    }
}
