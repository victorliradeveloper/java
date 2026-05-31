package com.ecommerce.product.domain.port.out;

import com.ecommerce.product.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(Long id);

    List<Product> findByCategory(String category);

    Product save(Product product);

    boolean existsById(Long id);

    void deleteById(Long id);
}
