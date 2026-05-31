package com.exemplo.datastructure.list;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/checkout")
    public Map<String, Object> checkout(@RequestParam(defaultValue = "0.10") BigDecimal tax,
                                        @RequestParam(defaultValue = "0") BigDecimal discount) {
        log.info("GET /api/list/checkout?tax={}&discount={}", tax, discount);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Product p : cart) {
            subtotal = subtotal.add(p.price());
            byCategory.merge(p.category(), p.price(), BigDecimal::add);
        }
        BigDecimal taxAmount = subtotal.multiply(tax).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount).subtract(discount).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", cart.size());
        result.put("subtotal", subtotal.setScale(2, RoundingMode.HALF_UP));
        result.put("tax", taxAmount);
        result.put("discount", discount.setScale(2, RoundingMode.HALF_UP));
        result.put("total", total);
        result.put("byCategory", byCategory);
        return result;
    }
}
