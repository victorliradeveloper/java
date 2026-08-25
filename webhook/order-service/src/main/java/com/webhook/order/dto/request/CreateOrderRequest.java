package com.webhook.order.dto.request;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String product,
        BigDecimal amount
) {}
