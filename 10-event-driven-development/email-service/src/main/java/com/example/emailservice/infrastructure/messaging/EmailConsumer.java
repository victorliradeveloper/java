package com.example.emailservice.infrastructure.messaging;

import com.example.emailservice.application.email.EmailService;
import com.example.emailservice.interfaces.dto.OrderEventDTO;
import com.example.emailservice.interfaces.dto.UserEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.REGISTERED_QUEUE)
    public void onUserRegistered(UserEventDTO event) {
        log.info("Received USER_REGISTERED for userId={}", event.payload().userId());
        emailService.sendUserRegistered(event.payload());
    }

    @RabbitListener(queues = RabbitMQConfig.LOGIN_QUEUE)
    public void onUserLogin(UserEventDTO event) {
        log.info("Received USER_LOGIN for userId={}", event.payload().userId());
        emailService.sendUserLogin(event.payload());
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onOrderCreated(OrderEventDTO event) {
        log.info("Received ORDER_CREATED for orderId={}", event.payload().orderId());
        emailService.sendOrderCreated(event.payload());
    }

    @RabbitListener(queues = RabbitMQConfig.PASSWORD_QUEUE)
    public void onPasswordReset(UserEventDTO event) {
        log.info("Received USER_PASSWORD_RESET for userId={}", event.payload().userId());
        emailService.sendPasswordReset(event.payload());
    }
}
