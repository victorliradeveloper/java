package com.ecommerce.payment.infrastructure.adapter.in.web.dto;

import com.ecommerce.payment.domain.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String currency,
        String stripePaymentIntentId,
        String stripeClientSecret,
        String status,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStripePaymentIntentId(),
                payment.getStripeClientSecret(),
                payment.getStatus().name(),
                payment.getCreatedAt()
        );
    }
}
