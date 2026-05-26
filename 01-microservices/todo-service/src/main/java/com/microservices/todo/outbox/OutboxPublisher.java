package com.microservices.todo.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.infrastructure.entity.OutboxEvent;
import com.microservices.todo.infrastructure.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class OutboxPublisher {

    private static final int MAX_LAST_ERROR_LENGTH = 2000;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final BackoffPolicy backoffPolicy;

    // Self-injection pra que publishOne(@Transactional REQUIRES_NEW) passe pelo
    // proxy do Spring — chamada direta this.publishOne(...) ignoraria a TX nova.
    // @Lazy quebra o ciclo: Spring injeta um proxy resolvido sob demanda.
    // Construtor escrito a mao porque Lombok nao propaga @Lazy pro parametro.
    private final OutboxPublisher self;

    private String nodeId;

    public OutboxPublisher(OutboxEventRepository repository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper,
                           OutboxProperties properties,
                           BackoffPolicy backoffPolicy,
                           @Lazy OutboxPublisher self) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.backoffPolicy = backoffPolicy;
        this.self = self;
    }

    @PostConstruct
    void initNodeId() {
        try {
            this.nodeId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            this.nodeId = "unknown-" + UUID.randomUUID();
        }
        log.info("[OUTBOX] publisher iniciado nodeId={}", nodeId);
    }

    // SpEL aqui referencia o property direto porque @Scheduled resolve o
    // fixedDelayString no parse-time da anotacao, antes da injecao de OutboxProperties.
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        Duration lease = Duration.ofMillis(properties.leaseDurationMs());
        for (int i = 0; i < properties.batchSize(); i++) {
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
            rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), payload);
            event.markPublished();
            log.info("[OUTBOX] publicado id={} exchange={} rk={} eventType={}",
                    event.getId(), event.getExchange(), event.getRoutingKey(), event.getEventType());
        } catch (Exception e) {
            event.markFailed(truncate(e.toString()), backoffPolicy::nextAttemptAt);
            log.warn("[OUTBOX] falha id={} attempts={} nextAttemptAt={}: {}",
                    event.getId(), event.getAttempts(), event.getNextAttemptAt(), e.getMessage());
        }
        repository.save(event);
    }

    private String truncate(String s) {
        return s != null && s.length() > MAX_LAST_ERROR_LENGTH
                ? s.substring(0, MAX_LAST_ERROR_LENGTH)
                : s;
    }
}
