package com.microservices.audit.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro imutavel de uma mudanca em um Todo. Append-only.
 *
 * <p>O {@code eventId} (PK) eh um UUID gerado pelo todo-service no momento em
 * que a mudanca ocorre. Funciona como chave natural de dedupe: se a chamada
 * for retentada pelo cliente (timeout, retry do Resilience4j, network blip),
 * o segundo INSERT cai em {@code ON CONFLICT DO NOTHING} e nada acontece.
 *
 * <p>Sem coluna {@code updated_at}: registros de auditoria sao imutaveis por
 * definicao. Qualquer "edicao" seria um novo registro com outro {@code action}.
 */
@Entity
@Table(
        name = "todo_audit_log",
        indexes = {
                @Index(name = "idx_audit_aggregate", columnList = "aggregate_id, occurred_at"),
                @Index(name = "idx_audit_event_type_occurred", columnList = "event_type, occurred_at")
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoAuditLog {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(length = 512)
    private String title;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;
}
