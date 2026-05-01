package com.example.userservice.infrastructure.messaging;

import java.time.Instant;

public record UserEventDTO(
        String eventType,
        Instant timestamp,
        Payload payload
) {
    public record Payload(String userId, String name, String email) {}
}
