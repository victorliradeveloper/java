package com.webhook.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public ProcessedWebhookEvent(UUID eventId, Instant receivedAt) {
        this.eventId = eventId;
        this.receivedAt = receivedAt;
    }

    public UUID eventId() {
        return eventId;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
