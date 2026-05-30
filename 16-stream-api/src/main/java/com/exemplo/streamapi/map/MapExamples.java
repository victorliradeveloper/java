package com.exemplo.streamapi.map;

import com.exemplo.streamapi.model.Product;
import com.exemplo.streamapi.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapExamples {

    private static final BigDecimal DISCOUNT_FACTOR = new BigDecimal("0.90");

    private final ProductRepository repository;

    public record ProductSummary(Long id, String name) {}

    @GetMapping("/names")
    public List<String> names() {
        log.info("GET /api/map/names");
        return repository.findAll().stream()
                .map(Product::name)
                .toList();
    }

    @GetMapping("/discounted-prices")
    public List<BigDecimal> discountedPrices() {
        log.info("GET /api/map/discounted-prices");
        return repository.findAll().stream()
                .map(p -> p.price().multiply(DISCOUNT_FACTOR).setScale(2, RoundingMode.HALF_UP))
                .toList();
    }

    @GetMapping("/summaries")
    public List<ProductSummary> summaries() {
        log.info("GET /api/map/summaries");
        return repository.findAll().stream()
                .map(p -> new ProductSummary(p.id(), p.name()))
                .toList();
    }
}
