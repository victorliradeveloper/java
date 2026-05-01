package com.example.emailservice.interfaces.dto;

import java.time.Instant;

public record UserEventDTO(
        String eventType,
        Instant timestamp,
        Payload payload
) {
    public record Payload(String userId, String name, String email) {}
}
