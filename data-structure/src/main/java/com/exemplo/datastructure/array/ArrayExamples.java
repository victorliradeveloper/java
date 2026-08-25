package com.exemplo.datastructure.array;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Array: tamanho fixo definido na criacao, acesso O(1) por indice.
 * Ideal quando o tamanho e conhecido e nao muda - aqui um snapshot
 * imutavel do catalogo carregado do repository.
 */
@Slf4j
@RestController
@RequestMapping("/api/array")
public class ArrayExamples {

    private final Product[] catalog;

    public ArrayExamples(ProductRepository repository) {
        this.catalog = repository.findAll().toArray(new Product[0]);
    }

    @GetMapping("/get")
    public Product get(@RequestParam int index) {
        log.info("GET /api/array/get?index={}", index);
        return catalog[index];
    }

    @GetMapping("/length")
    public int length() {
        log.info("GET /api/array/length");
        return catalog.length;
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/array/all");
        return Arrays.asList(catalog);
    }

    @GetMapping("/copy")
    public List<Product> copy(@RequestParam int from, @RequestParam int to) {
        log.info("GET /api/array/copy?from={}&to={}", from, to);
        Product[] slice = Arrays.copyOfRange(catalog, from, to);
        return Arrays.asList(slice);
    }

    @GetMapping("/contains")
    public boolean contains(@RequestParam Long id) {
        log.info("GET /api/array/contains?id={}", id);
        for (Product p : catalog) {
            if (p.id().equals(id)) return true;
        }
        return false;
    }

    @GetMapping("/filter-by-price")
    public List<Product> filterByPrice(@RequestParam BigDecimal max) {
        log.info("GET /api/array/filter-by-price?max={}", max);
        return Arrays.stream(catalog)
                .filter(p -> p.price().compareTo(max) < 0)
                .toList();
    }

    @GetMapping("/moving-average-price")
    public List<Map<String, Object>> movingAveragePrice(
            @RequestParam(defaultValue = "3") int window
    ) {
        log.info("GET /api/array/moving-average-price?window={}", window);

        if (window <= 0 || window > catalog.length) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        BigDecimal divisor = BigDecimal.valueOf(window);

        for (int i = 0; i <= catalog.length - window; i++) {
            BigDecimal sum = BigDecimal.ZERO;

            for (int j = i; j < i + window; j++) {
                sum = sum.add(catalog[j].price());
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", catalog[i].name());
            entry.put("to", catalog[i + window - 1].name());
            entry.put("average", sum.divide(divisor, 2, RoundingMode.HALF_UP));

            result.add(entry);
        }

        return result;
    }
}
