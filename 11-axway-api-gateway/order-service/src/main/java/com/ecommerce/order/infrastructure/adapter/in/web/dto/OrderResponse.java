package com.ecommerce.order.infrastructure.adapter.in.web.dto;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt
) {
    public record OrderItemResponse(
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal price
    ) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getQuantity(), item.getPrice());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
