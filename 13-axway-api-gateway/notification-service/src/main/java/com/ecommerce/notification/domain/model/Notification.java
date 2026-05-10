package com.ecommerce.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Notification {

    private final Long id;
    private final Long userId;
    private final Long orderId;
    private final NotificationType type;
    private NotificationStatus status;
    private final String recipient;
    private final String subject;
    private final String message;
    private String errorMessage;
    private final LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public static Notification newPending(
            Long userId, Long orderId, NotificationType type,
            String recipient, String subject, String message
    ) {
        return Notification.builder()
                .userId(userId)
                .orderId(orderId)
                .type(type)
                .status(NotificationStatus.PENDING)
                .recipient(recipient)
                .subject(subject)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = reason;
    }
}
