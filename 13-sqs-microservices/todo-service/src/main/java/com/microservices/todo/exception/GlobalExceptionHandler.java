package com.microservices.todo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TodoNotFoundException.class)
    public ProblemDetail handleTodoNotFound(TodoNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Todo not found");
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency-Key conflict");
        problem.setProperty("code", ex.getReason().name());
        problem.setProperty("idempotencyKey", ex.getKey());
        return problem;
    }

    // Bean validation (@Valid) em @RequestBody falha com MethodArgumentNotValidException.
    // Sem este handler, o Spring devolve o ProblemDetail default (sem detalhe dos campos).
    // Aqui adicionamos um mapa campo -> mensagem pra resposta ser programavel pelo cliente.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // merge: se o mesmo campo tem varios erros, mantem a primeira mensagem.
            errors.merge(fieldError.getField(), fieldError.getDefaultMessage(), (first, next) -> first);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos sao invalidos");
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return problem;
    }
}
