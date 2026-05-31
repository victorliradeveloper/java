package com.exemplo.datastructure.stack;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Stack (LIFO - Last In First Out). A classe legada java.util.Stack e
 * desencorajada; o padrao moderno e Deque/ArrayDeque com push/pop/peek.
 * Aqui modela "ultimos produtos visualizados": o mais recente fica no topo.
 */
@Slf4j
@RestController
@RequestMapping("/api/stack")
public class StackExamples {

    private final ProductRepository repository;
    private final Deque<Product> recentlyViewed = new ArrayDeque<>();

    public StackExamples(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/stack/all -> topo a esquerda, {} itens", recentlyViewed.size());
        return List.copyOf(recentlyViewed);
    }

    @GetMapping("/push")
    public List<Product> push(@RequestParam Long id) {
        log.info("GET /api/stack/push?id={}", id);
        repository.findById(id).ifPresent(recentlyViewed::push);
        return List.copyOf(recentlyViewed);
    }

    @GetMapping("/pop")
    public Product pop() {
        log.info("GET /api/stack/pop");
        return recentlyViewed.isEmpty() ? null : recentlyViewed.pop();
    }

    @GetMapping("/peek")
    public Product peek() {
        log.info("GET /api/stack/peek");
        return recentlyViewed.peek();
    }

    @GetMapping("/size")
    public int size() {
        log.info("GET /api/stack/size");
        return recentlyViewed.size();
    }

    @GetMapping("/undo")
    public List<Product> undo(@RequestParam(defaultValue = "1") int steps) {
        log.info("GET /api/stack/undo?steps={}", steps);
        List<Product> undone = new ArrayList<>();
        for (int i = 0; i < steps && !recentlyViewed.isEmpty(); i++) {
            undone.add(recentlyViewed.pop());
        }
        return undone;
    }
}
