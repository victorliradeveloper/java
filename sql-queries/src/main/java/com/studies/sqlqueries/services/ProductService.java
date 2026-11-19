package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Product;
import com.studies.sqlqueries.repositories.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> findByPriceRange(Double min, Double max) {
        return productRepository.findByPriceBetween(min, max);
    }

    public List<Product> findLowStock(int threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
    }

    public List<Product> findByTag(String tag) {
        return productRepository.findByTagsContaining(tag);
    }

    public List<Product> findByCategoryAndMaxPrice(String category, Double maxPrice) {
        return productRepository.findByCategoryAndPriceLessThanEqual(category, maxPrice);
    }

    public List<Product> findByAllTags(List<String> tags) {
        return productRepository.findByAllTags(tags);
    }

    public List<Product> findByColor(String color) {
        return productRepository.findByColor(color);
    }

    public List<Product> findInStockByAnyTag(List<String> tags) {
        return productRepository.findInStockByAnyTag(tags);
    }

    public List<Product> findByCategoriesAndPriceRange(List<String> categories, Double min, Double max) {
        return productRepository.findByCategoriesAndPriceRange(categories, min, max);
    }

    public List<Product> findRatedByUser(Long userId) {
        return productRepository.findRatedByUser(userId);
    }

    public List<Product> findByMinRating(int minScore) {
        return productRepository.findByMinimumRatingScore(minScore);
    }

    public List<Product> findWithOnlyHighRatings(int minScore) {
        return productRepository.findWithOnlyHighRatings(minScore);
    }

    public List<Product> findByCategoryWithMinRatingAndMaxPrice(String category, int minRating, Double maxPrice) {
        return productRepository.findByCategoryWithMinRatingAndMaxPrice(category, minRating, maxPrice);
    }

    public List<Product> findByCategoryOrTag(String category, String tag) {
        return productRepository.findByCategoryOrTag(category, tag);
    }

    public List<Product> findDeals(String category, Double maxPrice, int minRating) {
        return productRepository.findCategoryDeals(category, maxPrice, minRating);
    }
}
