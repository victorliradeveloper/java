package com.webhook.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        UUID orderId,
        BigDecimal amount
) {}
