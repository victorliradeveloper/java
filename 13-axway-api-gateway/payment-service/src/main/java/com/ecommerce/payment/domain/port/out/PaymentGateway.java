package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.model.GatewayPaymentIntent;

import java.math.BigDecimal;

public interface PaymentGateway {
    GatewayPaymentIntent createPaymentIntent(BigDecimal amount, String currency, Long orderId);
}
