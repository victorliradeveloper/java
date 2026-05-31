package com.ecommerce.payment.domain.model.event;

import com.ecommerce.payment.domain.model.Payment;

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
) {
    public static PaymentResultEvent of(Payment payment, String status, String failureReason) {
        return new PaymentResultEvent(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getAmount(),
                payment.getCurrency(),
                status,
                failureReason
        );
    }
}
