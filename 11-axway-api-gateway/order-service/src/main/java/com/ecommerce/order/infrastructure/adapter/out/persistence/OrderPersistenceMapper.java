package com.ecommerce.order.infrastructure.adapter.out.persistence;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toJpaEntity(Order order) {
        var entity = OrderJpaEntity.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(new ArrayList<>())
                .build();

        order.getItems().forEach(item -> {
            var itemEntity = OrderItemJpaEntity.builder()
                    .id(item.getId())
                    .order(entity)
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build();
            entity.getItems().add(itemEntity);
        });
        return entity;
    }

    public Order toDomain(OrderJpaEntity entity) {
        var items = entity.getItems().stream()
                .map(this::toDomainItem)
                .toList();

        return Order.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .items(items)
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private OrderItem toDomainItem(OrderItemJpaEntity entity) {
        return OrderItem.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .build();
    }
}
