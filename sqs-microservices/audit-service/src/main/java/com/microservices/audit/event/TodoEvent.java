package com.microservices.audit.event;

import java.time.LocalDateTime;

/**
 * Mesmo shape do TodoEvent do todo-service e notification-service. Mantido
 * duplicado de proposito — cada microservice tem sua propria copia do contrato,
 * sem dependencia cruzada. Compartilhar via lib comum eh acoplamento que volta
 * a quebrar quando schemas evoluem em ritmos diferentes.
 */
public record TodoEvent(
        String todoId,
        String title,
        String action,
        LocalDateTime occurredAt
) {}
