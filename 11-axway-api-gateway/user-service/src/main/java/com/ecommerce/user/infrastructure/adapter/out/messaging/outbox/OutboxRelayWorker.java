package com.ecommerce.user.infrastructure.adapter.out.messaging.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OutboxRelayWorker {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;

    private final OutboxJpaRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public OutboxRelayWorker(
            OutboxJpaRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchange}") String exchange
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    @Transactional
    public void relay() {
        var batch = outboxRepository.findUnpublished(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        log.debug("Outbox relay: dispatching {} entries", batch.size());

        for (var entry : batch) {
            try {
                var message = buildMessage(entry.getPayload());
                rabbitTemplate.send(exchange, entry.getRoutingKey(), message);
                entry.markPublished();
            } catch (Exception e) {
                log.error("Outbox relay failed for entry {}: {}", entry.getId(), e.getMessage());
                entry.markFailed(e.getMessage());
            }
        }
    }

    private Message buildMessage(String jsonPayload) {
        var props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        return new Message(jsonPayload.getBytes(StandardCharsets.UTF_8), props);
    }
}
