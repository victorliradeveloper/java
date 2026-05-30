package com.exemplo.datastructure.map;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Map: associacao chave-valor. Aqui id do produto -> Product,
 * que e exatamente o padrao usado em caches por id.
 *  - HashMap: O(1) put/get, sem ordem.
 *  - LinkedHashMap: O(1), preserva ordem de insercao.
 *  - TreeMap: O(log n), ordena pelas chaves (ids crescentes).
 */
@Slf4j
@RestController
@RequestMapping("/api/map")
public class MapExamples {

    private final ProductRepository repository;
    private final Map<Long, Product> hashMap;
    private final Map<Long, Product> linkedHashMap;
    private final TreeMap<Long, Product> treeMap;

    public MapExamples(ProductRepository repository) {
        this.repository = repository;
        this.hashMap = new HashMap<>(toIndex(repository));
        this.linkedHashMap = new LinkedHashMap<>(toIndex(repository));
        this.treeMap = new TreeMap<>(toIndex(repository));
    }

    private static Map<Long, Product> toIndex(ProductRepository repository) {
        return repository.findAll().stream().collect(Collectors.toMap(Product::id, Function.identity()));
    }

    @GetMapping("/hash")
    public Map<Long, Product> hash() {
        log.info("GET /api/map/hash -> {} entradas", hashMap.size());
        return hashMap;
    }

    @GetMapping("/linked")
    public Map<Long, Product> linked() {
        log.info("GET /api/map/linked -> {} entradas", linkedHashMap.size());
        return linkedHashMap;
    }

    @GetMapping("/tree")
    public Map<Long, Product> tree() {
        log.info("GET /api/map/tree (ordenado por id) -> {} entradas", treeMap.size());
        return treeMap;
    }

    @GetMapping("/put")
    public Product put(@RequestParam Long id) {
        log.info("GET /api/map/put?id={}", id);
        Product product = repository.findById(id).orElse(null);
        if (product == null) return null;
        Product previous = hashMap.put(id, product);
        linkedHashMap.put(id, product);
        treeMap.put(id, product);
        log.info("valor anterior em hashMap: {}", previous);
        return previous;
    }

    @GetMapping("/get")
    public Product get(@RequestParam Long id) {
        log.info("GET /api/map/get?id={}", id);
        return hashMap.get(id);
    }

    @GetMapping("/keys")
    public Set<Long> keys() {
        log.info("GET /api/map/keys");
        return hashMap.keySet();
    }

    @GetMapping("/get-or-default")
    public Product getOrDefault(@RequestParam Long id) {
        log.info("GET /api/map/get-or-default?id={}", id);
        Product fallback = new Product(-1L, "produto inexistente", null, "n/a", 0);
        return hashMap.getOrDefault(id, fallback);
    }
}
