package com.microservices.todo.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.todo.infrastructure.entity.OutboxEvent;
import com.microservices.todo.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Grava o evento na collection outbox_events. Roda dentro da transacao do
     * service chamador (TodoService) — save do Todo + insert no outbox commitam
     * juntos ou nenhum dos dois. Sem @Transactional proprio: herda a TX externa.
     */
    public void record(String destination, String aggregateId, String eventType, Object payload) {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .aggregateId(aggregateId)
                .aggregateType("Todo")
                .eventType(eventType)
                .destination(destination)
                .payload(serialize(payload))
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build();
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
