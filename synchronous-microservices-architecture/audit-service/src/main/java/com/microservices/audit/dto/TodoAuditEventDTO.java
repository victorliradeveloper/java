package com.microservices.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Payload recebido do todo-service via HTTP. Mesmo formato do {@code TodoEvent}
 * usado na versao com mensageria — so muda o transporte (REST vs AMQP).
 *
 * <p>{@code eventId} eh o identificador unico do evento gerado pelo emissor
 * (todo-service). Funciona como chave natural de idempotencia: se o cliente
 * retentar a mesma chamada (timeout, network blip, retry do Resilience4j), o
 * audit-service detecta o conflito e nao duplica o registro.
 */
public record TodoAuditEventDTO(
        @NotBlank String eventId,
        @NotBlank String todoId,
        String title,
        @NotBlank String action,
        @NotNull LocalDateTime occurredAt
) {}
