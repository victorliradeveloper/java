package com.microservices.todo.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.todo.infrastructure.entity.OutboxEvent;
import com.microservices.todo.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Grava eventos pendentes na tabela {@code outbox_events}.
 *
 * <p>Eh chamado pelo {@code TodoService} dentro da mesma {@code @Transactional}
 * do save da entidade: o save do {@code Todo} + o insert no outbox commitam
 * juntos, ou nenhum dos dois. Garantia atomica entre estado de negocio e
 * intencao de publicar.
 *
 * <p>Nao tem {@code @Transactional} proprio — herda a TX externa
 * propositalmente. Se este metodo abrisse uma TX nova, os dois inserts
 * poderiam divergir em caso de falha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final OutboxTracePropagator tracePropagator;

    public void record(String exchange,
                       String routingKey,
                       String aggregateId,
                       String aggregateType,
                       String eventType,
                       Object payload) {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .exchange(exchange)
                .routingKey(routingKey)
                .payload(serialize(payload))
                .createdAt(LocalDateTime.now())
                .attempts(0)
                // Captura o trace da request atual pra restaurar no publish assincrono.
                .traceParent(tracePropagator.capture())
                .build();
        log.info("[OUTBOX] enfileirado id={} aggregateId={} aggregateType={} eventType={} routingKey={}",
                event.getId(), aggregateId, aggregateType, eventType, routingKey);
        repository.save(event);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento outbox", e);
        }
    }
}
