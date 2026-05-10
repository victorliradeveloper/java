package com.ecommerce.order.domain.model.event;

import com.ecommerce.order.domain.model.Order;

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

    public static OrderCreatedEvent from(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderItemEvent(i.getProductId(), i.getProductName(), i.getQuantity(), i.getPrice()))
                .toList();
        return new OrderCreatedEvent(order.getId(), order.getUserId(), order.getTotalAmount(), "BRL", items);
    }
}
