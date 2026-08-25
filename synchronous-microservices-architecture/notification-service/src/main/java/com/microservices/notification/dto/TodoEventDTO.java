package com.microservices.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Payload recebido do todo-service via HTTP. Mesmo formato do {@code TodoEvent}
 * usado na versao com mensageria — so muda o transporte (REST vs AMQP).
 *
 * <p>{@code eventId} eh o identificador unico do evento, gerado pelo emissor
 * (todo-service). Funciona como chave de idempotencia: se a chamada for
 * retentada (timeout, retry do Resilience4j), o notification-service detecta
 * e nao envia o email de novo.
 */
public record TodoEventDTO(
        @NotBlank String eventId,
        @NotBlank String todoId,
        String title,
        @NotBlank String action,
        @NotNull LocalDateTime occurredAt
) {}
