package com.ecommerce.order.domain.port.in.command;

import java.util.List;

public record CreateOrderCommand(Long userId, List<OrderItemCommand> items) {}
