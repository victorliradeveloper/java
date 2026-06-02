package com.webhook.payment.dto.response;

import com.webhook.payment.domain.Payment;
import com.webhook.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id(), payment.orderId(), payment.amount(), payment.status());
    }
}
