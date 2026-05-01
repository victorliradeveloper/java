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
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserRegistered(User user) {
        UserEventDTO event = buildUserEvent("USER_REGISTERED", user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.REGISTERED_KEY, event);
        log.info("Published USER_REGISTERED for userId={}", user.getId());
    }

    public void publishUserLogin(User user) {
        UserEventDTO event = buildUserEvent("USER_LOGIN", user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.LOGIN_KEY, event);
        log.info("Published USER_LOGIN for userId={}", user.getId());
    }

    public void publishPasswordReset(User user) {
        UserEventDTO event = buildUserEvent("USER_PASSWORD_RESET", user);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.PASSWORD_KEY, event);
        log.info("Published USER_PASSWORD_RESET for userId={}", user.getId());
    }

    public void publishOrderCreated(Order order, User user) {
        OrderEventDTO event = new OrderEventDTO(
                "ORDER_CREATED",
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
        log.info("Published ORDER_CREATED for orderId={}", order.getId());
    }

    private UserEventDTO buildUserEvent(String eventType, User user) {
        return new UserEventDTO(
                eventType,
                Instant.now(),
                new UserEventDTO.Payload(
                        user.getId() != null ? user.getId().toString() : null,
                        user.getName(),
                        user.getEmail()
                )
        );
    }
}
