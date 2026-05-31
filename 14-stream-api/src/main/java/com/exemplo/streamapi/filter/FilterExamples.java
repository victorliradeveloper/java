package com.exemplo.streamapi.filter;

import com.exemplo.streamapi.model.Product;
import com.exemplo.streamapi.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/filter")
@RequiredArgsConstructor
public class FilterExamples {

    private final ProductRepository repository;

    @GetMapping("/in-stock")
    public List<Product> inStock() {
        log.info("GET /api/filter/in-stock");
        List<Product> result = repository.findAll().stream()
                .filter(p -> p.stock() > 0)
                .toList();
        log.info("in-stock result ({} items): {}", result.size(), result);
        return result;
    }

    @GetMapping("/by-category")
    public List<Product> byCategory(@RequestParam String category) {
        log.info("GET /api/filter/by-category?category={}", category);
        return repository.findAll().stream()
                .filter(p -> category.equalsIgnoreCase(p.category()))
                .toList();
    }

    @GetMapping("/price-range")
    public List<Product> priceRange(
            @RequestParam BigDecimal min,
            @RequestParam BigDecimal max
    ) {
        log.info("GET /api/filter/price-range?min={}&max={}", min, max);
        return repository.findAll().stream()
                .filter(p -> p.price().compareTo(min) >= 0 && p.price().compareTo(max) <= 0)
                .toList();
    }
}
