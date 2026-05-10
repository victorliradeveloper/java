package com.ecommerce.order.domain.port.in;

import com.ecommerce.order.domain.model.OrderStatus;

public interface UpdateOrderStatusUseCase {
    void updateStatus(Long orderId, OrderStatus status);
}
