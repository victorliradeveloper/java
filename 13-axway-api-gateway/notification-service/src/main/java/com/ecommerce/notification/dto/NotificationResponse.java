package com.ecommerce.notification.dto;

import com.ecommerce.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long orderId,
        String type,
        String status,
        String recipient,
        String subject,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getOrderId(), n.getType().name(),
                n.getStatus().name(), n.getRecipient(), n.getSubject(),
                n.getCreatedAt(), n.getSentAt()
        );
    }
}
