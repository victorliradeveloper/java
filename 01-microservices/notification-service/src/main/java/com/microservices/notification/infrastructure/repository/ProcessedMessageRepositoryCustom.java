package com.microservices.notification.infrastructure.repository;

import java.time.LocalDateTime;

public interface ProcessedMessageRepositoryCustom {

    /**
     * Tenta gravar o messageId atomicamente. Retorna {@code true} se inseriu
     * (primeira vez visto), {@code false} se ja existia (duplicata).
     *
     * <p>Implementado com {@code INSERT ... ON CONFLICT DO NOTHING} (Postgres) —
     * mesma semantica do {@code upsert + $setOnInsert} usado no Mongo do 15.
     * Atomico: nao tem janela de race entre check e insert.
     */
    boolean tryInsert(String messageId);

    /** Apaga linhas mais antigas que o cutoff. Retorna quantidade removida. */
    int deleteProcessedBefore(LocalDateTime cutoff);
}
