package com.microservices.notification.infrastructure.repository;

import java.time.LocalDateTime;

public interface ProcessedEventRepositoryCustom {

    /**
     * Tenta gravar o eventId atomicamente. Retorna {@code true} se inseriu
     * (primeira vez visto), {@code false} se ja existia (duplicata).
     *
     * <p>Implementado com {@code INSERT ... ON CONFLICT DO NOTHING} (Postgres).
     * Atomico: nao tem janela de race entre check e insert.
     */
    boolean tryInsert(String eventId);

    /** Apaga linhas mais antigas que o cutoff. Retorna quantidade removida. */
    int deleteProcessedBefore(LocalDateTime cutoff);
}
