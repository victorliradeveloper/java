package com.ecommerce.notification.event;

import java.math.BigDecimal;

public record PaymentResultEvent(
        Long orderId,
        Long userId,
        Long paymentId,
        String stripePaymentIntentId,
        BigDecimal amount,
        String currency,
        String status,
        String failureReason
) {}
