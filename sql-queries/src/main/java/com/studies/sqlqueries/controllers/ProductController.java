package com.studies.sqlqueries.controllers;

import com.studies.sqlqueries.entities.Product;
import com.studies.sqlqueries.services.ProductService;
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
    public List<Product> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/category/{category}")
    public List<Product> findByCategory(@PathVariable String category) {
        return productService.findByCategory(category);
    }

    // SQL: WHERE price BETWEEN :min AND :max
    @GetMapping("/price-range")
    public List<Product> findByPriceRange(@RequestParam Double min, @RequestParam Double max) {
        return productService.findByPriceRange(min, max);
    }

    @GetMapping("/low-stock")
    public List<Product> findLowStock(@RequestParam(defaultValue = "10") int threshold) {
        return productService.findLowStock(threshold);
    }

    // ==================== MEDIUM ====================

    // SQL: JOIN product_tags WHERE tag = ?
    @GetMapping("/tag/{tag}")
    public List<Product> findByTag(@PathVariable String tag) {
        return productService.findByTag(tag);
    }

    @GetMapping("/category/{category}/max-price/{maxPrice}")
    public List<Product> findByCategoryAndMaxPrice(
            @PathVariable String category,
            @PathVariable Double maxPrice) {
        return productService.findByCategoryAndMaxPrice(category, maxPrice);
    }

    // SQL: COUNT of matching tags = requested tag count — equivalent to MongoDB $all
    // ?tags=programming&tags=java
    @GetMapping("/all-tags")
    public List<Product> findByAllTags(@RequestParam List<String> tags) {
        return productService.findByAllTags(tags);
    }

    // SQL: JOIN product_attributes WHERE attr_key='color' AND attr_value=?
    @GetMapping("/color/{color}")
    public List<Product> findByColor(@PathVariable String color) {
        return productService.findByColor(color);
    }

    // SQL: JOIN product_tags WHERE tag IN (?) AND stock_quantity > 0
    // ?tags=running&tags=shoes
    @GetMapping("/in-stock/tags")
    public List<Product> findInStockByAnyTag(@RequestParam List<String> tags) {
        return productService.findInStockByAnyTag(tags);
    }

    // SQL: WHERE category IN (?) AND price BETWEEN ? AND ?
    // ?categories=Books&categories=Electronics&min=0&max=100
    @GetMapping("/categories/price-range")
    public List<Product> findByCategoriesAndPriceRange(
            @RequestParam List<String> categories,
            @RequestParam Double min,
            @RequestParam Double max) {
        return productService.findByCategoriesAndPriceRange(categories, min, max);
    }

    // ==================== COMPLEX ====================

    // SQL: JOIN product_ratings WHERE user_id = ?
    @GetMapping("/rated-by/{userId}")
    public List<Product> findRatedByUser(@PathVariable Long userId) {
        return productService.findRatedByUser(userId);
    }

    // SQL: JOIN product_ratings WHERE score >= ?
    @GetMapping("/min-rating/{minScore}")
    public List<Product> findByMinRating(@PathVariable int minScore) {
        return productService.findByMinRating(minScore);
    }

    // SQL: NOT EXISTS (bad rating) AND EXISTS (at least one rating)
    @GetMapping("/only-high-ratings/{minScore}")
    public List<Product> findWithOnlyHighRatings(@PathVariable int minScore) {
        return productService.findWithOnlyHighRatings(minScore);
    }

    // SQL: category + JOIN ratings for score threshold + price ceiling
    // ?category=Books&minRating=4&maxPrice=50
    @GetMapping("/search")
    public List<Product> search(
            @RequestParam String category,
            @RequestParam int minRating,
            @RequestParam Double maxPrice) {
        return productService.findByCategoryWithMinRatingAndMaxPrice(category, minRating, maxPrice);
    }

    // SQL: LEFT JOIN tags + WHERE category=? OR tag=?
    // ?category=Books&tag=java
    @GetMapping("/category-or-tag")
    public List<Product> findByCategoryOrTag(
            @RequestParam String category,
            @RequestParam String tag) {
        return productService.findByCategoryOrTag(category, tag);
    }

    // SQL: category + price < ? + stock > 0 + JOIN ratings where score >= ?
    // ?category=Books&maxPrice=45&minRating=4
    @GetMapping("/deals")
    public List<Product> findDeals(
            @RequestParam String category,
            @RequestParam Double maxPrice,
            @RequestParam(defaultValue = "4") int minRating) {
        return productService.findDeals(category, maxPrice, minRating);
    }
}
