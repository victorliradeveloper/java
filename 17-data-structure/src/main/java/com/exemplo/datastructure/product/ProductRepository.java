package com.exemplo.datastructure.product;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class ProductRepository {

    private final List<Product> products = List.of(
            new Product(1L, "Notebook Dell XPS 13", new BigDecimal("8999.90"), "eletronicos", 0),
            new Product(2L, "Mouse Logitech MX Master 3", new BigDecimal("549.00"), "eletronicos", 0),
            new Product(3L, "Teclado Mecanico Keychron K2", new BigDecimal("899.90"), "eletronicos", 23),
            new Product(4L, "Cadeira Gamer DXRacer", new BigDecimal("2199.00"), "moveis", 8),
            new Product(5L, "Monitor LG UltraWide 34", new BigDecimal("3499.00"), "eletronicos", 15),
            new Product(6L, "Mesa de Escritorio Madeira", new BigDecimal("1299.00"), "moveis", 5),
            new Product(7L, "Cafe em Graos 1kg", new BigDecimal("89.90"), "alimentos", 120),
            new Product(8L, "Garrafa Termica Stanley", new BigDecimal("349.00"), "utilidades", 34),
            new Product(9L, "Livro Clean Code", new BigDecimal("119.00"), "livros", 60),
            new Product(10L, "Headphone Sony WH-1000XM5", new BigDecimal("2499.00"), "eletronicos", 18)
    );

    public List<Product> findAll() {
        return products;
    }

    public Optional<Product> findById(Long id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<Product> findByCategory(String category) {
        return products.stream().filter(p -> p.category().equalsIgnoreCase(category)).toList();
    }
}
