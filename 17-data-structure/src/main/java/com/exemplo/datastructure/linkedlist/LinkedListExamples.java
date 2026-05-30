package com.exemplo.datastructure.linkedlist;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;

/**
 * LinkedList: lista duplamente encadeada. Insercao/remocao nas pontas O(1),
 * mas acesso por indice O(n). Aqui modela o historico de produtos visitados:
 * o mais recente entra na frente, o mais antigo sai do fim.
 */
@Slf4j
@RestController
@RequestMapping("/api/linked-list")
public class LinkedListExamples {

    private final ProductRepository repository;
    private final LinkedList<Product> history = new LinkedList<>();

    public LinkedListExamples(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/linked-list/all -> {} itens", history.size());
        return history;
    }

    @GetMapping("/add-first")
    public List<Product> addFirst(@RequestParam Long id) {
        log.info("GET /api/linked-list/add-first?id={}", id);
        repository.findById(id).ifPresent(history::addFirst);
        return history;
    }

    @GetMapping("/add-last")
    public List<Product> addLast(@RequestParam Long id) {
        log.info("GET /api/linked-list/add-last?id={}", id);
        repository.findById(id).ifPresent(history::addLast);
        return history;
    }

    @GetMapping("/remove-first")
    public Product removeFirst() {
        log.info("GET /api/linked-list/remove-first");
        return history.isEmpty() ? null : history.removeFirst();
    }

    @GetMapping("/remove-last")
    public Product removeLast() {
        log.info("GET /api/linked-list/remove-last");
        return history.isEmpty() ? null : history.removeLast();
    }

    @GetMapping("/peek-first")
    public Product peekFirst() {
        log.info("GET /api/linked-list/peek-first");
        return history.peekFirst();
    }

    @GetMapping("/peek-last")
    public Product peekLast() {
        log.info("GET /api/linked-list/peek-last");
        return history.peekLast();
    }
}
