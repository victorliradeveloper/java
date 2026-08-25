package com.microservices.notification.event;

import java.time.LocalDateTime;

public record TodoEvent(
        String todoId,
        String title,
        String action,
        LocalDateTime occurredAt
) {}
