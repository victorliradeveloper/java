package com.ecommerce.product.infrastructure.adapter.in.web;

import com.ecommerce.product.domain.port.in.FindProductsUseCase;
import com.ecommerce.product.domain.port.in.ManageProductsUseCase;
import com.ecommerce.product.domain.port.in.command.CreateProductCommand;
import com.ecommerce.product.domain.port.in.command.UpdateProductCommand;
import com.ecommerce.product.infrastructure.adapter.in.web.dto.ProductRequest;
import com.ecommerce.product.infrastructure.adapter.in.web.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final FindProductsUseCase findProductsUseCase;
    private final ManageProductsUseCase manageProductsUseCase;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll(
            @RequestParam(required = false) String category
    ) {
        var products = (category != null)
                ? findProductsUseCase.findByCategory(category)
                : findProductsUseCase.findAll();

        return ResponseEntity.ok(products.stream().map(ProductResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.from(findProductsUseCase.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        var command = new CreateProductCommand(
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.category()
        );
        var created = manageProductsUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        var command = new UpdateProductCommand(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.category()
        );
        return ResponseEntity.ok(ProductResponse.from(manageProductsUseCase.update(command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        manageProductsUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
