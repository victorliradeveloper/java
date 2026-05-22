package com.microservices.todo.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

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

    // null = pendente. Indexado pra acelerar o claim do publisher.
    @Field("published_at")
    @Indexed
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

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
        this.processingNode = null;
        this.leaseExpiresAt = null;
    }

    // Libera o lease pra que o proximo ciclo do publisher tente de novo.
    // Sem backoff por enquanto — retry imediato (~poll-interval depois).
    public void markFailed(String reason) {
        this.attempts++;
        this.lastError = reason;
        this.processingNode = null;
        this.leaseExpiresAt = null;
    }
}
