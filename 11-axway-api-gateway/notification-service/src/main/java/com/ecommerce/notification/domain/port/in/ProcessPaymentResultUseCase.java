package com.ecommerce.notification.domain.port.in;

import com.ecommerce.notification.domain.model.event.PaymentResultEvent;

public interface ProcessPaymentResultUseCase {
    void onPaymentSuccess(PaymentResultEvent event);

    void onPaymentFailed(PaymentResultEvent event);
}
