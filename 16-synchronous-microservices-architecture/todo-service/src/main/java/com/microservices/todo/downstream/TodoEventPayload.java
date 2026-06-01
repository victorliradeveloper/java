package com.microservices.todo.downstream;

import java.time.LocalDateTime;

/**
 * Payload enviado aos downstreams (audit-service, notification-service) via HTTP.
 *
 * <p>Substituto do antigo {@code TodoEvent} (que era serializado pra AMQP). Mesmo
 * formato, mais um {@code eventId} explicito como chave de idempotencia — antes
 * vinha do header AMQP {@code message-id}, agora vai no proprio body.
 */
public record TodoEventPayload(
        String eventId,
        String todoId,
        String title,
        String action,
        LocalDateTime occurredAt
) {
    public static TodoEventPayload of(String eventId, String todoId, String title, String action) {
        return new TodoEventPayload(eventId, todoId, title, action, LocalDateTime.now());
    }
}
