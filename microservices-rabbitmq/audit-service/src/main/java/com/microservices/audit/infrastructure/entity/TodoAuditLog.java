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
 * <p>O {@code messageId} (PK) eh o id AMQP setado pelo {@code OutboxPublisher} no
 * todo-service. Funciona como chave natural de dedupe: se a mesma mensagem for
 * reentregue pelo RabbitMQ (at-least-once), o segundo INSERT cai em
 * {@code ON CONFLICT DO NOTHING} e nada acontece. Sem precisar de tabela
 * processed_messages separada — a propria insercao da auditoria eh a verificacao
 * atomica.
 *
 * <p>Sem coluna {@code updated_at}: registros de auditoria sao imutaveis por
 * definicao. Qualquer "edicao" seria um novo registro com outro {@code action}.
 */
@Entity
@Table(
        name = "todo_audit_log",
        indexes = {
                // Acelera consultas "historico de um Todo".
                @Index(name = "idx_audit_aggregate", columnList = "aggregate_id, occurred_at"),
                // Acelera consultas por tipo de evento + janela de tempo.
                @Index(name = "idx_audit_event_type_occurred", columnList = "event_type, occurred_at")
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoAuditLog {

    @Id
    @Column(name = "message_id", length = 128)
    private String messageId;

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
