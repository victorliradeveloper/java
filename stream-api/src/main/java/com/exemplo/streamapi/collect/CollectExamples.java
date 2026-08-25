package com.exemplo.streamapi.collect;

import com.exemplo.streamapi.model.Product;
import com.exemplo.streamapi.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/collect")
@RequiredArgsConstructor
public class CollectExamples {

    private final ProductRepository repository;

    @GetMapping("/by-category")
    public Map<String, List<Product>> byCategory() {
        log.info("GET /api/collect/by-category");
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(Product::category));
    }

    @GetMapping("/price-sum-by-category")
    public Map<String, BigDecimal> priceSumByCategory() {
        log.info("GET /api/collect/price-sum-by-category");
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Product::category,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Product::price,
                                BigDecimal::add)));
    }

    @GetMapping("/availability")
    public Map<Boolean, List<Product>> availability() {
        log.info("GET /api/collect/availability");
        return repository.findAll().stream()
                .collect(Collectors.partitioningBy(p -> p.stock() > 0));
    }

    @GetMapping("/id-to-name")
    public Map<Long, String> idToName() {
        log.info("GET /api/collect/id-to-name");
        return repository.findAll().stream()
                .collect(Collectors.toMap(Product::id, Product::name));
    }
}
