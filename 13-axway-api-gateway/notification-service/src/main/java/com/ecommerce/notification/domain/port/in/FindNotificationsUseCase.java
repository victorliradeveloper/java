package com.ecommerce.notification.domain.port.in;

import com.ecommerce.notification.domain.model.Notification;

import java.util.List;

public interface FindNotificationsUseCase {
    List<Notification> findByUserId(Long userId);
}
