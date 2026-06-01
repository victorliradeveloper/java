package com.microservices.notification.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "processed_events",
        indexes = @Index(name = "idx_processed_events_processed_at", columnList = "processed_at")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    /** UUID do evento gerado pelo todo-service. */
    @Id
    @Column(length = 128)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;
}
