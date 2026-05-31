package com.ecommerce.notification.domain.port.in;

import com.ecommerce.notification.domain.model.event.UserProfileCreatedEvent;

public interface SendWelcomeEmailUseCase {
    void onProfileCreated(UserProfileCreatedEvent event);
}
