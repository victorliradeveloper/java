package com.example.userservice.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventDTO(
        String eventType,
        Instant timestamp,
        Payload payload
) {
    public record Payload(
            String orderId,
            String userId,
            String name,
            String email,
            String description,
            BigDecimal amount
    ) {}
}
