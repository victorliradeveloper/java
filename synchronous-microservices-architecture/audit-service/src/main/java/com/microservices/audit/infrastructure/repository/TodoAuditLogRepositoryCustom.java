package com.microservices.audit.infrastructure.repository;

import com.microservices.audit.infrastructure.entity.TodoAuditLog;

public interface TodoAuditLogRepositoryCustom {

    /**
     * Insere o log de auditoria de forma idempotente.
     *
     * <p>Implementado com {@code INSERT ... ON CONFLICT (event_id) DO NOTHING}:
     * se o {@code eventId} ja existe (retry do cliente), o INSERT eh ignorado
     * sem exception. Retorno:
     * <ul>
     *   <li>{@code true}  — inseriu (primeira vez visto)</li>
     *   <li>{@code false} — duplicata (ja existia)</li>
     * </ul>
     */
    boolean insertIfAbsent(TodoAuditLog log);
}
