package com.ecommerce.notification.infrastructure.adapter.in.messaging;

import com.ecommerce.notification.domain.model.event.PaymentResultEvent;
import com.ecommerce.notification.domain.port.in.ProcessPaymentResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultRabbitListener {

    private final ProcessPaymentResultUseCase processPaymentResultUseCase;

    @RabbitListener(queues = "${rabbitmq.payment-processed.queue}")
    public void handlePaymentSuccess(PaymentResultEvent event) {
        log.info("Received payment success for order: {}", event.orderId());
        processPaymentResultUseCase.onPaymentSuccess(event);
    }

    @RabbitListener(queues = "${rabbitmq.payment-failed.queue}")
    public void handlePaymentFailed(PaymentResultEvent event) {
        log.info("Received payment failed for order: {}", event.orderId());
        processPaymentResultUseCase.onPaymentFailed(event);
    }
}
