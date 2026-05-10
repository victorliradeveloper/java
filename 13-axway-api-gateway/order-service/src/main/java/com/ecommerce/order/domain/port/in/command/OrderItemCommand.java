package com.ecommerce.order.domain.port.in.command;

import java.math.BigDecimal;

public record OrderItemCommand(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal price
) {}
