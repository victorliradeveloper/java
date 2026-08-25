package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.OutboxEvent;

import java.time.Duration;
import java.util.Optional;

public interface OutboxEventRepositoryCustom {

    /**
     * Reivindica atomicamente o proximo evento pendente: encontra um doc com
     * publishedAt == null e (leaseExpiresAt == null OR leaseExpiresAt < now),
     * seta processingNode + leaseExpiresAt = now + leaseDuration, retorna o
     * doc atualizado. Operacao atomica via findAndModify — multiplos workers
     * competem e cada doc soh eh entregue a um por vez.
     *
     * Retorna Optional.empty() se nao houver pendentes.
     */
    Optional<OutboxEvent> claimNext(String nodeId, Duration leaseDuration);
}
