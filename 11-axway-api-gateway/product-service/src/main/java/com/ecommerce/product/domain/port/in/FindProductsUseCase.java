package com.ecommerce.product.domain.port.in;

import com.ecommerce.product.domain.model.Product;

import java.util.List;

public interface FindProductsUseCase {

    List<Product> findAll();

    Product findById(Long id);

    List<Product> findByCategory(String category);
}
