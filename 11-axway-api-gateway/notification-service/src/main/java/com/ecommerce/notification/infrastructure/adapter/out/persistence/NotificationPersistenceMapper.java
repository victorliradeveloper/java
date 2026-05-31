package com.ecommerce.notification.infrastructure.adapter.out.persistence;

import com.ecommerce.notification.domain.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationPersistenceMapper {

    public NotificationJpaEntity toJpaEntity(Notification notification) {
        return NotificationJpaEntity.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .orderId(notification.getOrderId())
                .type(notification.getType())
                .status(notification.getStatus())
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .message(notification.getMessage())
                .errorMessage(notification.getErrorMessage())
                .createdAt(notification.getCreatedAt())
                .sentAt(notification.getSentAt())
                .build();
    }

    public Notification toDomain(NotificationJpaEntity entity) {
        return Notification.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .orderId(entity.getOrderId())
                .type(entity.getType())
                .status(entity.getStatus())
                .recipient(entity.getRecipient())
                .subject(entity.getSubject())
                .message(entity.getMessage())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .sentAt(entity.getSentAt())
                .build();
    }
}
