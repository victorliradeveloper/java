package com.exemplo.datastructure.deque;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Deque (Double-Ended Queue): insere e remove em ambas as pontas em O(1).
 * Pode atuar como Stack (push/pop) ou Queue (offer/poll).
 * ArrayDeque e a implementacao mais rapida para a maioria dos casos.
 */
@Slf4j
@RestController
@RequestMapping("/api/deque")
public class DequeExamples {

    private final ProductRepository repository;
    private final Deque<Product> deque;

    public DequeExamples(ProductRepository repository) {
        this.repository = repository;
        this.deque = new ArrayDeque<>(repository.findAll());
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/deque/all -> {} itens", deque.size());
        return List.copyOf(deque);
    }

    @GetMapping("/add-first")
    public List<Product> addFirst(@RequestParam Long id) {
        log.info("GET /api/deque/add-first?id={}", id);
        repository.findById(id).ifPresent(deque::addFirst);
        return List.copyOf(deque);
    }

    @GetMapping("/add-last")
    public List<Product> addLast(@RequestParam Long id) {
        log.info("GET /api/deque/add-last?id={}", id);
        repository.findById(id).ifPresent(deque::addLast);
        return List.copyOf(deque);
    }

    @GetMapping("/remove-first")
    public Product removeFirst() {
        log.info("GET /api/deque/remove-first");
        return deque.pollFirst();
    }

    @GetMapping("/remove-last")
    public Product removeLast() {
        log.info("GET /api/deque/remove-last");
        return deque.pollLast();
    }
}
