package com.webhook.order.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreationRequest(
        UUID orderId,
        BigDecimal amount
) {}
