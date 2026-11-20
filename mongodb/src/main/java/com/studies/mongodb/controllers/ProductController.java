package com.studies.mongodb.controllers;

import com.studies.mongodb.documents.ProductDocument;
import com.studies.mongodb.documents.embedded.RatingDocument;
import com.studies.mongodb.services.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductDocument> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDocument> findById(@PathVariable String id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/category/{category}")
    public List<ProductDocument> findByCategory(@PathVariable String category) {
        return productService.findByCategory(category);
    }

    @GetMapping("/price-range")
    public List<ProductDocument> findByPriceRange(@RequestParam Double min, @RequestParam Double max) {
        return productService.findByPriceRange(min, max);
    }

    @GetMapping("/low-stock")
    public List<ProductDocument> findLowStock(@RequestParam(defaultValue = "10") int threshold) {
        return productService.findLowStock(threshold);
    }

    // ==================== MEDIUM ====================

    @GetMapping("/tag/{tag}")
    public List<ProductDocument> findByTag(@PathVariable String tag) {
        return productService.findByTag(tag);
    }

    @GetMapping("/category/{category}/max-price/{maxPrice}")
    public List<ProductDocument> findByCategoryAndMaxPrice(
            @PathVariable String category,
            @PathVariable Double maxPrice) {
        return productService.findByCategoryAndMaxPrice(category, maxPrice);
    }

    // All tags must be present: ?tags=programming&tags=java
    @GetMapping("/all-tags")
    public List<ProductDocument> findByAllTags(@RequestParam List<String> tags) {
        return productService.findByAllTags(tags);
    }

    @GetMapping("/color/{color}")
    public List<ProductDocument> findByColor(@PathVariable String color) {
        return productService.findByColor(color);
    }

    // In stock + at least one matching tag: ?tags=running&tags=shoes
    @GetMapping("/in-stock/tags")
    public List<ProductDocument> findInStockByAnyTag(@RequestParam List<String> tags) {
        return productService.findInStockByAnyTag(tags);
    }

    // Categories filter + price range: ?categories=Books&categories=Electronics&min=0&max=100
    @GetMapping("/categories/price-range")
    public List<ProductDocument> findByCategoriesAndPriceRange(
            @RequestParam List<String> categories,
            @RequestParam Double min,
            @RequestParam Double max) {
        return productService.findByCategoriesAndPriceRange(categories, min, max);
    }

    // ==================== COMPLEX ====================

    @GetMapping("/rated-by/{userId}")
    public List<ProductDocument> findRatedByUser(@PathVariable String userId) {
        return productService.findRatedByUser(userId);
    }

    @GetMapping("/min-rating/{minScore}")
    public List<ProductDocument> findByMinRating(@PathVariable int minScore) {
        return productService.findByMinRating(minScore);
    }

    // Only products where EVERY rating is >= minScore
    @GetMapping("/only-high-ratings/{minScore}")
    public List<ProductDocument> findWithOnlyHighRatings(@PathVariable int minScore) {
        return productService.findWithOnlyHighRatings(minScore);
    }

    // Three-way filter: ?category=Books&minRating=4&maxPrice=50
    @GetMapping("/search")
    public List<ProductDocument> search(
            @RequestParam String category,
            @RequestParam int minRating,
            @RequestParam Double maxPrice) {
        return productService.findByCategoryWithMinRatingAndMaxPrice(category, minRating, maxPrice);
    }

    // Category OR tag match: ?category=Books&tag=java
    @GetMapping("/category-or-tag")
    public List<ProductDocument> findByCategoryOrTag(
            @RequestParam String category,
            @RequestParam String tag) {
        return productService.findByCategoryOrTag(category, tag);
    }

    // Best deals: in stock + below price + good rating: ?category=Books&maxPrice=45&minRating=4
    @GetMapping("/deals")
    public List<ProductDocument> findDeals(
            @RequestParam String category,
            @RequestParam Double maxPrice,
            @RequestParam(defaultValue = "4") int minRating) {
        return productService.findDeals(category, maxPrice, minRating);
    }

    // ==================== BASIC UPDATE ====================

    // PUT: substitui o documento inteiro — campos ausentes viram null
    @PutMapping("/{id}")
    public ResponseEntity<ProductDocument> fullReplace(
            @PathVariable String id,
            @RequestBody ProductDocument updated) {
        return ResponseEntity.ok(productService.fullReplace(id, updated));
    }

    // PATCH: $set — atualiza apenas o preço
    @PatchMapping("/{id}/price")
    public ResponseEntity<ProductDocument> updatePrice(
            @PathVariable String id,
            @RequestParam Double price) {
        return ResponseEntity.ok(productService.updatePrice(id, price));
    }

    // ==================== MEDIUM UPDATE ====================

    // PATCH: $inc — entrada ou saída de estoque (delta positivo ou negativo)
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductDocument> adjustStock(
            @PathVariable String id,
            @RequestParam int delta) {
        return ResponseEntity.ok(productService.adjustStock(id, delta));
    }

    // PATCH: $addToSet — adiciona tag somente se ainda não existir
    @PatchMapping("/{id}/tags")
    public ResponseEntity<ProductDocument> addTag(
            @PathVariable String id,
            @RequestParam String tag) {
        return ResponseEntity.ok(productService.addTag(id, tag));
    }

    // DELETE: $pull — remove uma tag do array
    @DeleteMapping("/{id}/tags/{tag}")
    public ResponseEntity<ProductDocument> removeTag(
            @PathVariable String id,
            @PathVariable String tag) {
        return ResponseEntity.ok(productService.removeTag(id, tag));
    }

    // POST: $push com $sort — adiciona avaliação e mantém o array ordenado por score
    @PostMapping("/{id}/ratings")
    public ResponseEntity<ProductDocument> addRating(
            @PathVariable String id,
            @RequestBody RatingDocument rating) {
        return ResponseEntity.ok(productService.addRating(id, rating));
    }

    // ==================== ADVANCED UPDATE ====================

    // PATCH bulk: $mul — reajuste de preço em massa por categoria
    // Ex: ?factor=1.1 → reajuste de 10% em todos os produtos da categoria
    @PatchMapping("/category/{category}/price")
    public ResponseEntity<Long> adjustCategoryPrice(
            @PathVariable String category,
            @RequestParam double factor) {
        return ResponseEntity.ok(productService.adjustCategoryPrice(category, factor));
    }

    // DELETE: $pull com condição — remove avaliação de um userId específico
    @DeleteMapping("/{id}/ratings/{userId}")
    public ResponseEntity<ProductDocument> removeRating(
            @PathVariable String id,
            @PathVariable String userId) {
        return ResponseEntity.ok(productService.removeRating(id, userId));
    }
}
