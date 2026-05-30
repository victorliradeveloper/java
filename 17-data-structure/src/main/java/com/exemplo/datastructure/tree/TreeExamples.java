package com.exemplo.datastructure.tree;

import com.exemplo.datastructure.product.Product;
import com.exemplo.datastructure.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Arvore Binaria de Busca (BST) implementada na mao para mostrar a estrutura.
 * Aqui cada no guarda um Product e usa o preco como chave de ordenacao:
 * produtos mais baratos vao a esquerda, mais caros a direita.
 * Travessias classicas: in-order (ordenado por preco),
 * pre-order (raiz primeiro), post-order (raiz por ultimo).
 */
@Slf4j
@RestController
@RequestMapping("/api/tree")
public class TreeExamples {

    private static class Node {
        Product value;
        Node left, right;
        Node(Product value) { this.value = value; }
    }

    private final ProductRepository repository;
    private Node root;

    public TreeExamples(ProductRepository repository) {
        this.repository = repository;
        for (Product p : repository.findAll()) {
            root = insert(root, p);
        }
    }

    @GetMapping("/insert")
    public List<Product> insert(@RequestParam Long id) {
        log.info("GET /api/tree/insert?id={}", id);
        repository.findById(id).ifPresent(p -> root = insert(root, p));
        return inOrder();
    }

    @GetMapping("/contains")
    public boolean contains(@RequestParam BigDecimal price) {
        log.info("GET /api/tree/contains?price={}", price);
        return contains(root, price);
    }

    @GetMapping("/in-order")
    public List<Product> inOrder() {
        log.info("GET /api/tree/in-order (esquerda -> raiz -> direita; ordena por preco)");
        List<Product> acc = new ArrayList<>();
        inOrder(root, acc);
        return acc;
    }

    @GetMapping("/pre-order")
    public List<Product> preOrder() {
        log.info("GET /api/tree/pre-order (raiz -> esquerda -> direita)");
        List<Product> acc = new ArrayList<>();
        preOrder(root, acc);
        return acc;
    }

    @GetMapping("/post-order")
    public List<Product> postOrder() {
        log.info("GET /api/tree/post-order (esquerda -> direita -> raiz)");
        List<Product> acc = new ArrayList<>();
        postOrder(root, acc);
        return acc;
    }

    private Node insert(Node node, Product product) {
        if (node == null) return new Node(product);
        int cmp = product.price().compareTo(node.value.price());
        if (cmp < 0) node.left = insert(node.left, product);
        else if (cmp > 0) node.right = insert(node.right, product);
        return node;
    }

    private boolean contains(Node node, BigDecimal price) {
        if (node == null) return false;
        int cmp = price.compareTo(node.value.price());
        if (cmp == 0) return true;
        return cmp < 0 ? contains(node.left, price) : contains(node.right, price);
    }

    private void inOrder(Node node, List<Product> acc) {
        if (node == null) return;
        inOrder(node.left, acc);
        acc.add(node.value);
        inOrder(node.right, acc);
    }

    private void preOrder(Node node, List<Product> acc) {
        if (node == null) return;
        acc.add(node.value);
        preOrder(node.left, acc);
        preOrder(node.right, acc);
    }

    private void postOrder(Node node, List<Product> acc) {
        if (node == null) return;
        postOrder(node.left, acc);
        postOrder(node.right, acc);
        acc.add(node.value);
    }
}
