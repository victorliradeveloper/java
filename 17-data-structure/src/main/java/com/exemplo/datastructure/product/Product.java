package com.exemplo.datastructure.product;

import java.math.BigDecimal;

public record Product(Long id, String name, BigDecimal price, String category, int stock) {
}
