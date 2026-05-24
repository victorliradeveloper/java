package com.microservices.todo.exception;

import lombok.Getter;

@Getter
public class IdempotencyKeyConflictException extends RuntimeException {

    public enum Reason {
        PAYLOAD_MISMATCH("payload difere do enviado na primeira chamada com esta key"),
        IN_PROGRESS("requisicao concorrente ainda em processamento"),
        INVALID_KEY("formato da key invalido (esperado ASCII imprimivel, max 255 chars)");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final String key;
    private final Reason reason;

    public IdempotencyKeyConflictException(String key, Reason reason) {
        super("Idempotency-Key '" + key + "' rejeitada: " + reason.message());
        this.key = key;
        this.reason = reason;
    }
}
