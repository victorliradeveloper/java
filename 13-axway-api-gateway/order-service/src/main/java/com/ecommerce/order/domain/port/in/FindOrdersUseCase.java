package com.ecommerce.order.domain.port.in;

import com.ecommerce.order.domain.model.Order;

import java.util.List;

public interface FindOrdersUseCase {
    List<Order> findByUserId(Long userId);

    Order findById(Long orderId, Long requesterUserId);
}
