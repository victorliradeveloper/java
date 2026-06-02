package com.webhook.order.controller;

import com.webhook.order.service.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handlePaymentServiceFailure(RestClientException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Payment service unavailable: " + ex.getMessage()
        );
    }
}
