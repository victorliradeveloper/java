package com.ecommerce.product.domain.port.in.command;

import java.math.BigDecimal;

public record UpdateProductCommand(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String category
) {}
