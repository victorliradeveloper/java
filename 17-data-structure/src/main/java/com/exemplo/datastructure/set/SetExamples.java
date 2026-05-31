package com.exemplo.datastructure.set;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Set: colecao sem duplicatas (Product e record, entao equals/hashCode
 * sao gerados automaticamente).
 *  - HashSet: O(1) add/contains, sem ordem.
 *  - LinkedHashSet: O(1), preserva ordem de insercao.
 *  - TreeSet: O(log n), aqui ordenado pelo preco via Comparator.
 */
@Slf4j
@RestController
@RequestMapping("/api/set")
public class SetExamples {

    private final ProductRepository repository;
    private final Set<Product> hashSet;
    private final Set<Product> linkedHashSet;
    private final TreeSet<Product> treeSet;

    public SetExamples(ProductRepository repository) {
        this.repository = repository;
        this.hashSet = new HashSet<>(repository.findAll());
        this.linkedHashSet = new LinkedHashSet<>(repository.findAll());
        this.treeSet = new TreeSet<>(Comparator.comparing(Product::price).thenComparing(Product::id));
        this.treeSet.addAll(repository.findAll());
    }

    @GetMapping("/hash")
    public Set<Product> hash() {
        log.info("GET /api/set/hash (sem ordem garantida) -> {} itens", hashSet.size());
        return hashSet;
    }

    @GetMapping("/linked")
    public Set<Product> linked() {
        log.info("GET /api/set/linked (ordem de insercao) -> {} itens", linkedHashSet.size());
        return linkedHashSet;
    }

    @GetMapping("/tree")
    public Set<Product> tree() {
        log.info("GET /api/set/tree (ordenado por preco) -> {} itens", treeSet.size());
        return treeSet;
    }

    @GetMapping("/add")
    public boolean add(@RequestParam Long id) {
        log.info("GET /api/set/add?id={}", id);
        Product product = repository.findById(id).orElse(null);
        if (product == null) return false;
        boolean added = hashSet.add(product);
        linkedHashSet.add(product);
        treeSet.add(product);
        log.info("hashSet retornou {} (false = ja existia)", added);
        return added;
    }

    @GetMapping("/contains")
    public boolean contains(@RequestParam Long id) {
        log.info("GET /api/set/contains?id={}", id);
        return repository.findById(id).map(hashSet::contains).orElse(false);
    }

    @GetMapping("/tree-cheapest")
    public Product treeCheapest() {
        log.info("GET /api/set/tree-cheapest");
        return treeSet.isEmpty() ? null : treeSet.first();
    }

    @GetMapping("/tree-most-expensive")
    public Product treeMostExpensive() {
        log.info("GET /api/set/tree-most-expensive");
        return treeSet.isEmpty() ? null : treeSet.last();
    }

    @GetMapping("/wishlist-status")
    public Map<String, Set<Long>> wishlistStatus(@RequestParam List<Long> ids) {
        log.info("GET /api/set/wishlist-status?ids={}", ids);
        Set<Long> catalogIds = repository.findAll().stream()
                .map(Product::id).collect(Collectors.toSet());
        Set<Long> inStockIds = repository.findAll().stream()
                .filter(Product::hasStock).map(Product::id).collect(Collectors.toSet());
        Set<Long> wishlist = new HashSet<>(ids);

        Set<Long> available = new HashSet<>(wishlist);
        available.retainAll(inStockIds);

        Set<Long> outOfStock = new HashSet<>(wishlist);
        outOfStock.retainAll(catalogIds);
        outOfStock.removeAll(inStockIds);

        Set<Long> unknown = new HashSet<>(wishlist);
        unknown.removeAll(catalogIds);

        Map<String, Set<Long>> result = new LinkedHashMap<>();
        result.put("available", available);
        result.put("outOfStock", outOfStock);
        result.put("unknown", unknown);
        return result;
    }
}
