package com.ecommerce.payment.infrastructure.adapter.out.messaging;

import com.ecommerce.payment.domain.model.event.PaymentResultEvent;
import com.ecommerce.payment.domain.port.out.PaymentEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitPaymentEventPublisher implements PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String paymentProcessedRoutingKey;
    private final String paymentFailedRoutingKey;

    public RabbitPaymentEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchange}") String exchange,
            @Value("${rabbitmq.payment-processed.routing-key}") String paymentProcessedRoutingKey,
            @Value("${rabbitmq.payment-failed.routing-key}") String paymentFailedRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.paymentProcessedRoutingKey = paymentProcessedRoutingKey;
        this.paymentFailedRoutingKey = paymentFailedRoutingKey;
    }

    @Override
    public void publishProcessed(PaymentResultEvent event) {
        rabbitTemplate.convertAndSend(exchange, paymentProcessedRoutingKey, event);
    }

    @Override
    public void publishFailed(PaymentResultEvent event) {
        rabbitTemplate.convertAndSend(exchange, paymentFailedRoutingKey, event);
    }
}
