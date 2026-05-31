package com.ecommerce.payment.infrastructure.adapter.in.messaging;

import com.ecommerce.payment.domain.model.event.OrderCreatedEvent;
import com.ecommerce.payment.domain.port.in.ProcessOrderPaymentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedRabbitListener {

    private final ProcessOrderPaymentUseCase processOrderPaymentUseCase;

    @RabbitListener(queues = "${rabbitmq.order-created.queue}")
    public void handle(OrderCreatedEvent event) {
        log.info("Received OrderCreated event for order: {}", event.orderId());
        processOrderPaymentUseCase.processPayment(event);
    }
}
