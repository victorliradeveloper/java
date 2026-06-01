package com.microservices.notification.exception;

/**
 * Falha real no envio SMTP. Marcada explicitamente pra que tanto o
 * {@code @CircuitBreaker} quanto o {@code @Retry} a tratem como falha
 * recuperavel (e nao confundam com bug de codigo).
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
