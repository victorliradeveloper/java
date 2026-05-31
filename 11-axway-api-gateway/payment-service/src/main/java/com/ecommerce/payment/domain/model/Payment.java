package com.ecommerce.payment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Payment {

    private final Long id;
    private final Long orderId;
    private final Long userId;
    private final BigDecimal amount;
    private final String currency;
    private String stripePaymentIntentId;
    private String stripeClientSecret;
    private PaymentStatus status;
    private String failureReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Payment newProcessing(Long orderId, Long userId, BigDecimal amount, String currency) {
        var now = LocalDateTime.now();
        return Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markPending(GatewayPaymentIntent intent) {
        this.stripePaymentIntentId = intent.id();
        this.stripeClientSecret = intent.clientSecret();
        this.status = PaymentStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
}
