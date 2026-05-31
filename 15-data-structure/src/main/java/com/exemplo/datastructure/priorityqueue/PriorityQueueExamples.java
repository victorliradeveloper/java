package com.exemplo.datastructure.priorityqueue;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * PriorityQueue: fila baseada em heap binario. poll() devolve o elemento
 * de maior prioridade (segundo o Comparator). Ordem interna nao e linear -
 * apenas a raiz do heap esta garantida; para sair em ordem, use poll repetido.
 * Aqui: produto mais barato primeiro (minHeap por preco) e produto com mais
 * estoque primeiro (maxHeap por estoque) - casos classicos de fila de prioridade.
 */
@Slf4j
@RestController
@RequestMapping("/api/priority-queue")
public class PriorityQueueExamples {

    private static final Comparator<Product> BY_PRICE_ASC = Comparator.comparing(Product::price);
    private static final Comparator<Product> BY_STOCK_DESC = Comparator.comparingInt(Product::stock).reversed();

    private final ProductRepository repository;
    private final PriorityQueue<Product> cheapestFirst;
    private final PriorityQueue<Product> mostStockedFirst;

    public PriorityQueueExamples(ProductRepository repository) {
        this.repository = repository;
        this.cheapestFirst = new PriorityQueue<>(BY_PRICE_ASC);
        this.cheapestFirst.addAll(repository.findAll());
        this.mostStockedFirst = new PriorityQueue<>(BY_STOCK_DESC);
        this.mostStockedFirst.addAll(repository.findAll());
    }

    @GetMapping("/cheapest")
    public List<Product> drainCheapest() {
        log.info("GET /api/priority-queue/cheapest (drena em ordem crescente de preco)");
        PriorityQueue<Product> copy = new PriorityQueue<>(cheapestFirst);
        return drain(copy);
    }

    @GetMapping("/most-stocked")
    public List<Product> drainMostStocked() {
        log.info("GET /api/priority-queue/most-stocked (drena em ordem decrescente de estoque)");
        PriorityQueue<Product> copy = new PriorityQueue<>(mostStockedFirst);
        return drain(copy);
    }

    @GetMapping("/offer")
    public Product offer(@RequestParam Long id) {
        log.info("GET /api/priority-queue/offer?id={}", id);
        repository.findById(id).ifPresent(cheapestFirst::offer);
        return cheapestFirst.peek();
    }

    @GetMapping("/poll")
    public Product poll() {
        log.info("GET /api/priority-queue/poll (remove o mais barato)");
        return cheapestFirst.poll();
    }

    @GetMapping("/peek")
    public Product peek() {
        log.info("GET /api/priority-queue/peek (le o mais barato sem remover)");
        return cheapestFirst.peek();
    }

    private List<Product> drain(PriorityQueue<Product> pq) {
        return java.util.stream.Stream
                .generate(pq::poll)
                .takeWhile(java.util.Objects::nonNull)
                .toList();
    }

    @GetMapping("/top-cheapest-in-stock")
    public List<Product> topCheapestInStock(@RequestParam(defaultValue = "3") int k) {
        log.info("GET /api/priority-queue/top-cheapest-in-stock?k={}", k);
        PriorityQueue<Product> heap = new PriorityQueue<>(BY_PRICE_ASC);
        for (Product p : repository.findAll()) {
            if (p.hasStock()) heap.offer(p);
        }
        List<Product> top = new ArrayList<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            top.add(heap.poll());
        }
        return top;
    }
}
