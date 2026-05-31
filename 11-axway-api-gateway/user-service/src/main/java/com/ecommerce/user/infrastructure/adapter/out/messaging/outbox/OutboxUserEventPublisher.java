package com.ecommerce.user.infrastructure.adapter.out.messaging.outbox;

import com.ecommerce.user.domain.model.event.AddressAddedEvent;
import com.ecommerce.user.domain.model.event.UserProfileCreatedEvent;
import com.ecommerce.user.domain.model.event.UserProfileUpdatedEvent;
import com.ecommerce.user.domain.port.out.UserEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutboxUserEventPublisher implements UserEventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String profileCreatedRoutingKey;
    private final String profileUpdatedRoutingKey;
    private final String addressAddedRoutingKey;

    public OutboxUserEventPublisher(
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${rabbitmq.user-profile-created.routing-key}") String profileCreatedRoutingKey,
            @Value("${rabbitmq.user-profile-updated.routing-key}") String profileUpdatedRoutingKey,
            @Value("${rabbitmq.user-address-added.routing-key}") String addressAddedRoutingKey
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.profileCreatedRoutingKey = profileCreatedRoutingKey;
        this.profileUpdatedRoutingKey = profileUpdatedRoutingKey;
        this.addressAddedRoutingKey = addressAddedRoutingKey;
    }

    @Override
    public void publishProfileCreated(UserProfileCreatedEvent event) {
        save("UserProfile", String.valueOf(event.profileId()), "UserProfileCreated", profileCreatedRoutingKey, event);
    }

    @Override
    public void publishProfileUpdated(UserProfileUpdatedEvent event) {
        save("UserProfile", String.valueOf(event.profileId()), "UserProfileUpdated", profileUpdatedRoutingKey, event);
    }

    @Override
    public void publishAddressAdded(AddressAddedEvent event) {
        save("UserProfile", String.valueOf(event.profileId()), "AddressAdded", addressAddedRoutingKey, event);
    }

    private void save(String aggregateType, String aggregateId, String eventType, String routingKey, Object payload) {
        var entry = OutboxEntryJpaEntity.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .routingKey(routingKey)
                .payload(toJson(payload))
                .status(OutboxStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();
        outboxRepository.save(entry);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
