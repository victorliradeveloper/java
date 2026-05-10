package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.model.event.OrderCreatedEvent;

public interface OrderEventPublisher {
    void publishOrderCreated(OrderCreatedEvent event);
}
