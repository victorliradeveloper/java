package com.exemplo.streamapi.model;

import java.math.BigDecimal;

public record Product(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock
) {
}
