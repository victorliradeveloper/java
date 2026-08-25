package com.microservices.todo.idempotency;

import com.microservices.todo.infrastructure.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Substituto do TTL nativo do Mongo. Postgres nao tem expiracao automatica,
 * entao um job @Scheduled apaga claims vencidos periodicamente.
 *
 * Cadencia: a cada {@code idempotency.cleanup-interval} (default 1h). O delay
 * inicial evita rodar logo no startup, quando outras initializations estao
 * acontecendo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupJob {

    private final IdempotencyKeyRepository repository;

    @Scheduled(
            fixedDelayString = "${idempotency.cleanup-interval:PT1H}",
            initialDelayString = "${idempotency.cleanup-initial-delay:PT5M}"
    )
    @Transactional
    public void cleanupExpired() {
        int deleted = repository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("[IDEMPOTENCY-CLEANUP] {} claim(s) expirado(s) removido(s)", deleted);
        }
    }
}
