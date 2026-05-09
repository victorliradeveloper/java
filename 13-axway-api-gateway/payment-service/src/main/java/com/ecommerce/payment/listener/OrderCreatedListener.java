package com.ecommerce.payment.listener;

import com.ecommerce.payment.event.OrderCreatedEvent;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final PaymentService paymentService;

    @RabbitListener(queues = "${rabbitmq.order-created.queue}")
    public void handle(OrderCreatedEvent event) {
        log.info("Received OrderCreated event for order: {}", event.orderId());
        paymentService.processPayment(event);
    }
}
