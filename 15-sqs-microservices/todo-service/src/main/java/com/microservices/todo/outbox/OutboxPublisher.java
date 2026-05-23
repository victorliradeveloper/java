package com.microservices.todo.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.infrastructure.entity.OutboxEvent;
import com.microservices.todo.infrastructure.repository.OutboxEventRepository;
import io.awspring.cloud.sns.core.SnsTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final SnsTemplate snsTemplate;
    private final ObjectMapper objectMapper;

    // Self-injection pra que publishOne(@Transactional REQUIRES_NEW) passe pelo
    // proxy do Spring — chamada direta this.publishOne(...) ignoraria a TX nova.
    // @Lazy quebra o ciclo: Spring injeta um proxy resolvido sob demanda.
    // Construtor escrito a mao porque Lombok nao propaga @Lazy pro parametro.
    private final OutboxPublisher self;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    @Value("${outbox.lease-duration-ms:30000}")
    private long leaseDurationMs;

    public OutboxPublisher(OutboxEventRepository repository,
                           SnsTemplate snsTemplate,
                           ObjectMapper objectMapper,
                           @Lazy OutboxPublisher self) {
        this.repository = repository;
        this.snsTemplate = snsTemplate;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    private String nodeId;

    @PostConstruct
    void initNodeId() {
        try {
            this.nodeId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            this.nodeId = "unknown-" + UUID.randomUUID();
        }
        log.info("[OUTBOX] publisher iniciado nodeId={}", nodeId);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        Duration lease = Duration.ofMillis(leaseDurationMs);
        for (int i = 0; i < batchSize; i++) {
            Optional<OutboxEvent> claimed = repository.claimNext(nodeId, lease);
            if (claimed.isEmpty()) {
                return;
            }
            self.publishOne(claimed.get());
        }
    }

    // REQUIRES_NEW: cada evento publica/falha isoladamente — uma falha nao rola
    // back o lote inteiro. Save dentro da mesma TX nova garante que o estado
    // (publishedAt OU attempts++) eh persistido.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(OutboxEvent event) {
        try {
            TodoEvent payload = objectMapper.readValue(event.getPayload(), TodoEvent.class);
            // O header "action" eh propagado como SNS message attribute e usado
            // pelo FilterPolicy das subscriptions (ver init-aws.sh). Sem isso,
            // todas as filas receberiam todos os eventos.
            Map<String, Object> headers = Map.of("action", event.getEventType());
            snsTemplate.convertAndSend(event.getDestination(), payload, headers);
            event.markPublished();
            log.info("[OUTBOX] publicado id={} destination={} eventType={}",
                    event.getId(), event.getDestination(), event.getEventType());
        } catch (Exception e) {
            event.markFailed(truncate(e.toString()));
            log.warn("[OUTBOX] falha id={} attempts={}: {}",
                    event.getId(), event.getAttempts(), e.getMessage());
        }
        repository.save(event);
    }

    private String truncate(String s) {
        return s != null && s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
