package com.ecommerce.payment.domain.model.event;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemEvent> items
) {
    public record OrderItemEvent(
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal price
    ) {}
}
