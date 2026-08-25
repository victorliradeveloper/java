package com.exemplo.streamapi.model;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProductRepository {

    private static final List<Product> PRODUCTS = List.of(
            new Product(1L,  "Dell Notebook",          "Electronics", new BigDecimal("4500.00"), 8),
            new Product(2L,  "Logitech Mouse",         "Electronics", new BigDecimal("120.00"),  50),
            new Product(3L,  "Mechanical Keyboard",    "Electronics", new BigDecimal("350.00"),  0),
            new Product(4L,  "Gaming Chair",           "Furniture",   new BigDecimal("1800.00"), 5),
            new Product(5L,  "Office Desk",            "Furniture",   new BigDecimal("950.00"),  12),
            new Product(6L,  "Coffee Beans 1kg",       "Food",        new BigDecimal("65.00"),   100),
            new Product(7L,  "Dark Chocolate 70%",     "Food",        new BigDecimal("18.00"),   0),
            new Product(8L,  "Clean Code Book",        "Books",       new BigDecimal("110.00"),  20),
            new Product(9L,  "Effective Java Book",    "Books",       new BigDecimal("180.00"),  15),
            new Product(10L, "27\" 4K Monitor",        "Electronics", new BigDecimal("2200.00"), 3)
    );

    public List<Product> findAll() {
        return PRODUCTS;
    }
}
