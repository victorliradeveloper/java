package com.ecommerce.product.domain.port.in;

import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.port.in.command.CreateProductCommand;
import com.ecommerce.product.domain.port.in.command.UpdateProductCommand;

public interface ManageProductsUseCase {

    Product create(CreateProductCommand command);

    Product update(UpdateProductCommand command);

    void delete(Long id);
}
