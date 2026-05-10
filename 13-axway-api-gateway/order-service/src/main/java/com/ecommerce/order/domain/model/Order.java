package com.ecommerce.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Order {

    private final Long id;
    private final Long userId;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Order create(Long userId, List<OrderItem> items) {
        var total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var now = LocalDateTime.now();
        return Order.builder()
                .userId(userId)
                .items(List.copyOf(items))
                .totalAmount(total)
                .status(OrderStatus.AWAITING_PAYMENT)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void changeStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean ownedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
