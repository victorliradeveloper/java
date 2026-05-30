package com.exemplo.datastructure.queue;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Queue (FIFO - First In First Out). offer adiciona no fim,
 * poll remove do inicio. Diferente do add/remove que jogam excecao,
 * offer/poll retornam false/null - mais seguros.
 * Aqui modela uma fila de processamento de pedidos.
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
public class QueueExamples {

    private final ProductRepository repository;
    private final Queue<Product> orderQueue = new ArrayDeque<>();

    public QueueExamples(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/queue/all -> frente a esquerda, {} itens", orderQueue.size());
        return List.copyOf(orderQueue);
    }

    @GetMapping("/offer")
    public List<Product> offer(@RequestParam Long id) {
        log.info("GET /api/queue/offer?id={}", id);
        repository.findById(id).ifPresent(orderQueue::offer);
        return List.copyOf(orderQueue);
    }

    @GetMapping("/poll")
    public Product poll() {
        log.info("GET /api/queue/poll");
        return orderQueue.poll();
    }

    @GetMapping("/peek")
    public Product peek() {
        log.info("GET /api/queue/peek");
        return orderQueue.peek();
    }

    @GetMapping("/size")
    public int size() {
        log.info("GET /api/queue/size");
        return orderQueue.size();
    }
}
