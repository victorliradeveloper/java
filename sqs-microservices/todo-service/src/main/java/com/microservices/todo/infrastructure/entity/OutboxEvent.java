package com.microservices.todo.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.function.IntFunction;

@Document(collection = "outbox_events")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private String id;

    @Field("aggregate_id")
    private String aggregateId;

    @Field("aggregate_type")
    private String aggregateType;

    @Field("event_type")
    private String eventType;

    private String destination;

    private String payload;

    @Field("created_at")
    private LocalDateTime createdAt;

    // null = pendente. Indexado em V001_BaselineIndexes (Mongock) pra acelerar
    // o claim do publisher.
    @Field("published_at")
    private LocalDateTime publishedAt;

    private int attempts;

    @Field("last_error")
    private String lastError;

    // Lease pattern: worker reivindica o doc atomicamente setando esses campos.
    // Outros workers ignoram docs com lease ainda valida (lease_expires_at > now).
    @Field("processing_node")
    private String processingNode;

    @Field("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    // Quando o evento volta a ser elegivel pro claim depois de uma falha.
    // null = elegivel imediatamente (caso default pra evento recem-criado).
    // claimNext ignora docs com next_attempt_at > now.
    @Field("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
        this.processingNode = null;
        this.leaseExpiresAt = null;
        this.nextAttemptAt = null;
    }

    /**
     * Marca o evento como falho e agenda a proxima tentativa.
     *
     * <p>A entidade nao decide *quando* retentar — apenas registra a falha,
     * incrementa o contador e aplica o resultado da politica de retry recebida
     * via {@code nextAttemptResolver} (tipicamente {@code BackoffPolicy::nextAttemptAt}).
     * Isso mantem a entidade livre de dependencias de configuracao ou de geracao
     * de aleatorios — a politica eh testavel e substituivel de forma independente.
     */
    public void markFailed(String reason, IntFunction<LocalDateTime> nextAttemptResolver) {
        this.attempts++;
        this.lastError = reason;
        this.processingNode = null;
        this.leaseExpiresAt = null;
        this.nextAttemptAt = nextAttemptResolver.apply(this.attempts);
    }
}
