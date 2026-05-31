package com.microservices.notification.infrastructure.repository;

public interface ProcessedMessageRepositoryCustom {

    /**
     * Tenta gravar o messageId atomicamente. Retorna true se inseriu (primeira vez),
     * false se ja existia (duplicata). Implementado com upsert + $setOnInsert no Mongo
     * — equivalente semantico ao INSERT ... ON CONFLICT DO NOTHING do Postgres.
     */
    boolean tryInsert(String messageId);
}
