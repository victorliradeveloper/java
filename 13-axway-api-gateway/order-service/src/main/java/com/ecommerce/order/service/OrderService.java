package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.order-created.routing-key}")
    private String orderCreatedRoutingKey;

    public List<OrderResponse> findByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderResponse findById(Long id, Long userId) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied to order: " + id);
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse create(Long userId, OrderRequest request) {
        var items = request.items().stream()
                .map(i -> OrderItem.builder()
                        .productId(i.productId())
                        .productName(i.productName())
                        .quantity(i.quantity())
                        .price(i.price())
                        .build())
                .toList();

        var total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var order = Order.builder()
                .userId(userId)
                .totalAmount(total)
                .status(OrderStatus.AWAITING_PAYMENT)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        var saved = orderRepository.save(order);

        publishOrderCreatedEvent(saved);

        return OrderResponse.from(saved);
    }

    @Transactional
    public void updateStatus(Long orderId, OrderStatus status) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }

    private void publishOrderCreatedEvent(Order order) {
        var event = new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                "BRL",
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.OrderItemEvent(
                                i.getProductId(), i.getProductName(), i.getQuantity(), i.getPrice()
                        ))
                        .toList()
        );

        rabbitTemplate.convertAndSend(exchange, orderCreatedRoutingKey, event);
    }
}
