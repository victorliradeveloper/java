package com.ecommerce.payment.domain.port.in;

import com.ecommerce.payment.domain.model.event.OrderCreatedEvent;

public interface ProcessOrderPaymentUseCase {
    void processPayment(OrderCreatedEvent event);
}
