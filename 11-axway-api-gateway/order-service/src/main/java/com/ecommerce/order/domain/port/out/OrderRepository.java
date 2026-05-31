package com.ecommerce.order.domain.port.out;

import com.ecommerce.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    List<Order> findByUserId(Long userId);

    Optional<Order> findById(Long id);

    Order save(Order order);
}
