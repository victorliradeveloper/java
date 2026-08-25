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
        name = "processed_messages",
        indexes = @Index(name = "idx_processed_messages_processed_at", columnList = "processed_at")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {

    /** ID universal da mensagem AMQP (header {@code spring_returned_message_correlation} ou messageId). */
    @Id
    @Column(length = 128)
    private String messageId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;
}
