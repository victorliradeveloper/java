package com.microservices.todo.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "idempotency_keys",
        indexes = @Index(name = "idx_idempotency_keys_expires_at", columnList = "expires_at")
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(length = 255)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public void markCompleted(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }
}
