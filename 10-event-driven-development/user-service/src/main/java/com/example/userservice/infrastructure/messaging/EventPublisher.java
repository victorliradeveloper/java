package com.example.userservice.infrastructure.messaging;

import com.example.userservice.domain.model.Order;
import com.example.userservice.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserRegistered(User user) {
        UserEventDTO event = buildUserEvent(EventType.USER_REGISTERED, user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.REGISTERED_KEY, event);
        log.info("Published {} for userId={}", EventType.USER_REGISTERED, user.getId());
    }

    public void publishUserLogin(User user) {
        UserEventDTO event = buildUserEvent(EventType.USER_LOGIN, user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.LOGIN_KEY, event);
        log.info("Published {} for userId={}", EventType.USER_LOGIN, user.getId());
    }

    public void publishPasswordReset(User user) {
        UserEventDTO event = buildUserEvent(EventType.USER_PASSWORD_RESET, user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.PASSWORD_KEY, event);
        log.info("Published {} for userId={}", EventType.USER_PASSWORD_RESET, user.getId());
    }

    public void publishOrderCreated(Order order, User user) {
        OrderEventDTO event = new OrderEventDTO(
                EventType.ORDER_CREATED.name(),
                Instant.now(),
                new OrderEventDTO.Payload(
                        order.getId().toString(),
                        user.getId().toString(),
                        user.getName(),
                        user.getEmail(),
                        order.getDescription(),
                        order.getAmount()
                )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ORDER_KEY, event);
        log.info("Published {} for orderId={}", EventType.ORDER_CREATED, order.getId());
    }

    private UserEventDTO buildUserEvent(EventType eventType, User user) {
        if (user.getId() == null) {
            throw new IllegalStateException("Cannot publish event for user without ID");
        }
        return new UserEventDTO(
                eventType.name(),
                Instant.now(),
                new UserEventDTO.Payload(
                        user.getId().toString(),
                        user.getName(),
                        user.getEmail()
                )
        );
    }
}
