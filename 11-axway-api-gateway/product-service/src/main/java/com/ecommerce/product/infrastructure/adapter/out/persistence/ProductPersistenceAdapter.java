package com.ecommerce.product.infrastructure.adapter.out.persistence;

import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.port.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;
    private final ProductPersistenceMapper mapper;

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Product> findByCategory(String category) {
        return jpaRepository.findByCategory(category).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Product save(Product product) {
        var entity = mapper.toJpaEntity(product);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
