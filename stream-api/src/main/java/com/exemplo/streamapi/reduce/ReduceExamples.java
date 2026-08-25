package com.exemplo.streamapi.reduce;

import com.exemplo.streamapi.model.Product;
import com.exemplo.streamapi.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/reduce")
@RequiredArgsConstructor
public class ReduceExamples {

    private final ProductRepository repository;

    @GetMapping("/total-inventory-value")
    public BigDecimal totalInventoryValue() {
        log.info("GET /api/reduce/total-inventory-value");
        return repository.findAll().stream()
                .map(p -> p.price().multiply(BigDecimal.valueOf(p.stock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @GetMapping("/total-units")
    public int totalUnits() {
        log.info("GET /api/reduce/total-units");
        return repository.findAll().stream()
                .mapToInt(Product::stock)
                .sum();
    }

    @GetMapping("/most-expensive")
    public ResponseEntity<Product> mostExpensive() {
        log.info("GET /api/reduce/most-expensive");
        return repository.findAll().stream()
                .reduce((a, b) -> a.price().compareTo(b.price()) > 0 ? a : b)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
