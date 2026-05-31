package com.ecommerce.product.infrastructure.adapter.out.persistence;

import com.ecommerce.product.domain.model.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductPersistenceMapper {

    public ProductJpaEntity toJpaEntity(Product product) {
        return ProductJpaEntity.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public Product toDomain(ProductJpaEntity entity) {
        return Product.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .category(entity.getCategory())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
