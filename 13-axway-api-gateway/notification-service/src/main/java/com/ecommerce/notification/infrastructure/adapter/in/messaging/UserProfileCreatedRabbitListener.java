package com.ecommerce.notification.infrastructure.adapter.in.messaging;

import com.ecommerce.notification.domain.model.event.UserProfileCreatedEvent;
import com.ecommerce.notification.domain.port.in.SendWelcomeEmailUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileCreatedRabbitListener {

    private final SendWelcomeEmailUseCase sendWelcomeEmailUseCase;

    @RabbitListener(queues = "${rabbitmq.user-profile-created.queue}")
    public void handle(UserProfileCreatedEvent event) {
        log.info("Received UserProfileCreated for userId: {}", event.userId());
        sendWelcomeEmailUseCase.onProfileCreated(event);
    }
}
