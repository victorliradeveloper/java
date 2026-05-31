package com.ecommerce.notification.domain.model.event;

import java.time.LocalDateTime;

public record UserProfileCreatedEvent(
        Long profileId,
        Long userId,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {}
