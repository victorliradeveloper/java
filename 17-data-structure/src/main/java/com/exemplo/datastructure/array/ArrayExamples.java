package com.exemplo.datastructure.array;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

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
}
