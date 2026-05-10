package com.ecommerce.order.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity,
        @NotNull BigDecimal price
) {}
