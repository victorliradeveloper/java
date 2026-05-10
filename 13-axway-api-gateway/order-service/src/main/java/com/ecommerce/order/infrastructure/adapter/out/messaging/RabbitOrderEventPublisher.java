package com.ecommerce.order.infrastructure.adapter.out.messaging;

import com.ecommerce.order.domain.model.event.OrderCreatedEvent;
import com.ecommerce.order.domain.port.out.OrderEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitOrderEventPublisher implements OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String orderCreatedRoutingKey;

    public RabbitOrderEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchange}") String exchange,
            @Value("${rabbitmq.order-created.routing-key}") String orderCreatedRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.orderCreatedRoutingKey = orderCreatedRoutingKey;
    }

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(exchange, orderCreatedRoutingKey, event);
    }
}
