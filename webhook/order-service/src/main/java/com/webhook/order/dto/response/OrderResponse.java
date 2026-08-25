package com.webhook.order.dto.response;

import com.webhook.order.domain.Order;
import com.webhook.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String product,
        BigDecimal amount,
        OrderStatus status
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.product(), order.amount(), order.status());
    }
}
