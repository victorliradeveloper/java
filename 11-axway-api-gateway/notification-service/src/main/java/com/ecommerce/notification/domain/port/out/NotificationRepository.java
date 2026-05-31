package com.ecommerce.notification.domain.port.out;

import com.ecommerce.notification.domain.model.Notification;

import java.util.List;

public interface NotificationRepository {
    List<Notification> findByUserId(Long userId);

    Notification save(Notification notification);
}
