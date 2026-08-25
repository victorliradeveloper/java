package com.microservices.notification.infrastructure.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Apaga registros mais antigos que {@code processed-events.retention} pra
 * evitar crescimento ilimitado da tabela {@code processed_events}.
 *
 * <p>Janela tipica: 24h. Retentativas depois disso vao gerar processamento
 * duplicado — mas retry de cliente HTTP depois de 24h indica problema mais
 * serio que dedup nao resolve.
 */
@Slf4j
@Component
public class ProcessedEventCleanupJob {

    private final ProcessedEventRepository repository;
    private final Duration retention;

    public ProcessedEventCleanupJob(ProcessedEventRepository repository,
                                    @Value("${processed-events.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${processed-events.cleanup-interval:PT1H}",
            initialDelayString = "${processed-events.cleanup-initial-delay:PT5M}"
    )
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minus(retention);
        int removed = repository.deleteProcessedBefore(cutoff);
        if (removed > 0) {
            log.info("[DEDUPE-CLEANUP] {} processed_events removidas (anteriores a {})", removed, cutoff);
        }
    }
}
