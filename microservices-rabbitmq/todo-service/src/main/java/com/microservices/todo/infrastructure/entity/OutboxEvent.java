package com.microservices.todo.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.function.IntFunction;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                // Acelera o claim do publisher: filtra pendentes (published_at IS NULL)
                // e ordena por created_at. O Postgres usa este indice no SELECT FOR UPDATE.
                @Index(name = "idx_outbox_pending", columnList = "published_at, created_at")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    // Destino no RabbitMQ: dois campos em vez de um (como o SQS/SNS faria com ARN).
    // Mais natural ao modelo do RabbitMQ — publish eh sempre (exchange, routingKey).
    @Column(nullable = false, length = 128)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 128)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // W3C traceparent capturado no momento do enfileiramento. Restaurado pelo
    // publisher pra que a publicacao assincrona herde o trace da request original
    // (HTTP -> outbox -> AMQP -> consumer no mesmo traceId). null = enfileirado
    // sem contexto de trace ativo.
    @Column(name = "trace_parent", length = 64)
    private String traceParent;

    // null = pendente. Indexado em conjunto com created_at pra acelerar o claim.
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    // Lease pattern: worker reivindica a linha atomicamente setando esses campos.
    // Outros workers ignoram linhas com lease ainda valida (lease_expires_at > now).
    @Column(name = "processing_node", length = 128)
    private String processingNode;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    // Quando o evento volta a ser elegivel pro claim depois de uma falha.
    // null = elegivel imediatamente (caso default pra evento recem-criado).
    // claimNext ignora linhas com next_attempt_at > now.
    @Column(name = "next_attempt_at")
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
