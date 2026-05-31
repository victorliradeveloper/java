package com.exemplo.streamapi.sorted;

import com.exemplo.streamapi.model.Product;
import com.exemplo.streamapi.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sorted")
@RequiredArgsConstructor
public class SortedExamples {

    private final ProductRepository repository;

    @GetMapping("/by-name")
    public List<Product> byName() {
        log.info("GET /api/sorted/by-name");
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::name))
                .toList();
    }

    @GetMapping("/by-price-asc")
    public List<Product> byPriceAsc() {
        log.info("GET /api/sorted/by-price-asc");
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::price))
                .toList();
    }

    @GetMapping("/by-price-desc")
    public List<Product> byPriceDesc() {
        log.info("GET /api/sorted/by-price-desc");
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::price).reversed())
                .toList();
    }

    @GetMapping("/by-category-then-price")
    public List<Product> byCategoryThenPrice() {
        log.info("GET /api/sorted/by-category-then-price");
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::category)
                        .thenComparing(Product::price))
                .toList();
    }

    @GetMapping("/top-expensive")
    public List<Product> topExpensive(@RequestParam(defaultValue = "3") int limit) {
        log.info("GET /api/sorted/top-expensive?limit={}", limit);
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Product::price).reversed())
                .limit(limit)
                .toList();
    }
}
