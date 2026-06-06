package com.microservices.notification.infrastructure.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Substituto do TTL do Mongo. Apaga registros mais antigos que
 * {@code processed-messages.retention} pra evitar crescimento ilimitado
 * da tabela {@code processed_messages}.
 *
 * <p>Janela tipica: 24h. Mensagens redelivradas depois disso vao gerar
 * processamento duplicado — mas redelivery depois de 24h eh extremamente
 * raro em RabbitMQ (e indica problema mais serio que dedup nao resolve).
 */
@Slf4j
@Component
public class ProcessedMessageCleanupJob {

    private final ProcessedMessageRepository repository;
    private final Duration retention;

    public ProcessedMessageCleanupJob(ProcessedMessageRepository repository,
                                      @Value("${processed-messages.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${processed-messages.cleanup-interval:PT1H}",
            initialDelayString = "${processed-messages.cleanup-initial-delay:PT5M}"
    )
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minus(retention);
        int removed = repository.deleteProcessedBefore(cutoff);
        if (removed > 0) {
            log.info("[DEDUPE-CLEANUP] {} processed_messages removidas (anteriores a {})", removed, cutoff);
        }
    }
}
