package com.ecommerce.product.application.service;

import com.ecommerce.product.domain.exception.ProductNotFoundException;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.port.in.FindProductsUseCase;
import com.ecommerce.product.domain.port.in.ManageProductsUseCase;
import com.ecommerce.product.domain.port.in.command.CreateProductCommand;
import com.ecommerce.product.domain.port.in.command.UpdateProductCommand;
import com.ecommerce.product.domain.port.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService implements FindProductsUseCase, ManageProductsUseCase {

    private final ProductRepository productRepository;

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Override
    public Product create(CreateProductCommand command) {
        var product = Product.newProduct(
                command.name(),
                command.description(),
                command.price(),
                command.stock(),
                command.category()
        );
        return productRepository.save(product);
    }

    @Override
    public Product update(UpdateProductCommand command) {
        var product = productRepository.findById(command.id())
                .orElseThrow(() -> new ProductNotFoundException(command.id()));

        product.update(
                command.name(),
                command.description(),
                command.price(),
                command.stock(),
                command.category()
        );
        return productRepository.save(product);
    }

    @Override
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
