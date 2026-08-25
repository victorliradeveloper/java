package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.OutboxEvent;

import java.time.Duration;
import java.util.Optional;

public interface OutboxEventRepositoryCustom {

    /**
     * Reivindica atomicamente o proximo evento pendente.
     *
     * <p>Encontra uma linha com {@code published_at IS NULL} e
     * ({@code lease_expires_at IS NULL OR lease_expires_at < now}) e
     * ({@code next_attempt_at IS NULL OR next_attempt_at <= now}), seta
     * {@code processing_node + lease_expires_at = now + leaseDuration} e
     * retorna a linha atualizada.
     *
     * <p>A atomicidade vem de {@code SELECT ... FOR UPDATE SKIP LOCKED}: o
     * Postgres entrega cada linha a apenas um worker e os outros pulam sem
     * bloquear. Funciona com qualquer numero de workers concorrentes.
     *
     * @return {@code Optional.empty()} se nao houver pendente elegivel
     */
    Optional<OutboxEvent> claimNext(String nodeId, Duration leaseDuration);
}
