package com.ecommerce.order.domain.port.in;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.in.command.CreateOrderCommand;

public interface CreateOrderUseCase {
    Order create(CreateOrderCommand command);
}
