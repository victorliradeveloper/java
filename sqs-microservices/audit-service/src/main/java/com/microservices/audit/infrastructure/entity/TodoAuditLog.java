package com.microservices.audit.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Registro imutavel de uma mudanca em um Todo. Append-only.
 *
 * O _id eh o MessageId do SQS — natural dedupe key. Se o SQS reentregar a
 * mesma mensagem (at-least-once), o segundo insert falha com
 * DuplicateKeyException e o listener trata. Mais simples e correto que
 * tabela de processed_messages separada.
 */
@Document(collection = "todo_audit_log")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoAuditLog {

    @Id
    private String id;

    @Field("aggregate_id")
    private String aggregateId;

    private String title;

    @Field("event_type")
    private String eventType;

    @Field("occurred_at")
    private LocalDateTime occurredAt;

    @Field("recorded_at")
    private LocalDateTime recordedAt;
}
