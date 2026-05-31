package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.model.event.PaymentResultEvent;

public interface PaymentEventPublisher {
    void publishProcessed(PaymentResultEvent event);

    void publishFailed(PaymentResultEvent event);
}
