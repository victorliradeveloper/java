package com.exemplo.datastructure.list;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * List (ArrayList): tamanho dinamico, acesso O(1) por indice,
 * insercao no fim amortizada O(1), insercao no meio O(n).
 * Aqui representa um carrinho de compras montado a partir do catalogo.
 */
@Slf4j
@RestController
@RequestMapping("/api/list")
public class ListExamples {

    private final ProductRepository repository;
    private final List<Product> cart;

    public ListExamples(ProductRepository repository) {
        this.repository = repository;
        this.cart = new ArrayList<>(repository.findAll());
    }

    @GetMapping("/all")
    public List<Product> all() {
        log.info("GET /api/list/all -> {} itens", cart.size());
        return cart;
    }

    @GetMapping("/add")
    public List<Product> add(@RequestParam Long id) {
        log.info("GET /api/list/add?id={}", id);
        repository.findById(id).ifPresent(cart::add);
        return cart;
    }

    @GetMapping("/get")
    public Product get(@RequestParam int index) {
        log.info("GET /api/list/get?index={}", index);
        return cart.get(index);
    }

    @GetMapping("/remove")
    public List<Product> remove(@RequestParam int index) {
        log.info("GET /api/list/remove?index={}", index);
        cart.remove(index);
        return cart;
    }

    @GetMapping("/index-of")
    public int indexOf(@RequestParam Long id) {
        log.info("GET /api/list/index-of?id={}", id);
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    @GetMapping("/sub-list")
    public List<Product> subList(@RequestParam int from, @RequestParam int to) {
        log.info("GET /api/list/sub-list?from={}&to={}", from, to);
        return cart.subList(from, to);
    }
}
