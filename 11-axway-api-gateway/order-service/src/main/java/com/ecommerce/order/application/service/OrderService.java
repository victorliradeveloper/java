package com.ecommerce.order.application.service;

import com.ecommerce.order.domain.exception.OrderAccessDeniedException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.event.OrderCreatedEvent;
import com.ecommerce.order.domain.port.in.CreateOrderUseCase;
import com.ecommerce.order.domain.port.in.FindOrdersUseCase;
import com.ecommerce.order.domain.port.in.UpdateOrderStatusUseCase;
import com.ecommerce.order.domain.port.in.command.CreateOrderCommand;
import com.ecommerce.order.domain.port.out.OrderEventPublisher;
import com.ecommerce.order.domain.port.out.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements FindOrdersUseCase, CreateOrderUseCase, UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Order findById(Long orderId, Long requesterUserId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.ownedBy(requesterUserId)) {
            throw new OrderAccessDeniedException(orderId);
        }
        return order;
    }

    @Override
    @Transactional
    public Order create(CreateOrderCommand command) {
        var items = command.items().stream()
                .map(i -> OrderItem.builder()
                        .productId(i.productId())
                        .productName(i.productName())
                        .quantity(i.quantity())
                        .price(i.price())
                        .build())
                .toList();

        var order = Order.create(command.userId(), items);
        var saved = orderRepository.save(order);

        orderEventPublisher.publishOrderCreated(OrderCreatedEvent.from(saved));
        return saved;
    }

    @Override
    @Transactional
    public void updateStatus(Long orderId, OrderStatus status) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.changeStatus(status);
        orderRepository.save(order);
    }
}
