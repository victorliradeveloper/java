package com.studies.mongodb.services;

import com.studies.mongodb.documents.ProductDocument;
import com.studies.mongodb.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductDocument> findAll() {
        return productRepository.findAll();
    }

    public Optional<ProductDocument> findById(String id) {
        log.info("Hello World");
        log.info("ID: {}", id);
        return productRepository.findById(id);
    }

    public List<ProductDocument> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<ProductDocument> findByPriceRange(Double min, Double max) {
        return productRepository.findByPriceBetween(min, max);
    }

    public List<ProductDocument> findLowStock(int threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
    }

    public List<ProductDocument> findByTag(String tag) {
        return productRepository.findByTagsContaining(tag);
    }

    public List<ProductDocument> findByCategoryAndMaxPrice(String category, Double maxPrice) {
        return productRepository.findByCategoryAndPriceLessThanEqual(category, maxPrice);
    }

    public List<ProductDocument> findByAllTags(List<String> tags) {
        return productRepository.findByAllTags(tags);
    }

    public List<ProductDocument> findByColor(String color) {
        return productRepository.findByColor(color);
    }

    public List<ProductDocument> findInStockByAnyTag(List<String> tags) {
        return productRepository.findInStockByAnyTag(tags);
    }

    public List<ProductDocument> findByCategoriesAndPriceRange(List<String> categories, Double min, Double max) {
        return productRepository.findByCategoriesAndPriceRange(categories, min, max);
    }

    public List<ProductDocument> findRatedByUser(String userId) {
        return productRepository.findRatedByUser(userId);
    }

    public List<ProductDocument> findByMinRating(int minScore) {
        return productRepository.findByMinimumRatingScore(minScore);
    }

    public List<ProductDocument> findWithOnlyHighRatings(int minScore) {
        return productRepository.findWithOnlyHighRatings(minScore);
    }

    public List<ProductDocument> findByCategoryWithMinRatingAndMaxPrice(String category, int minRating, Double maxPrice) {
        return productRepository.findByCategoryWithMinRatingAndMaxPrice(category, minRating, maxPrice);
    }

    public List<ProductDocument> findByCategoryOrTag(String category, String tag) {
        return productRepository.findByCategoryOrTag(category, tag);
    }

    public List<ProductDocument> findDeals(String category, Double maxPrice, int minRating) {
        return productRepository.findCategoryDeals(category, maxPrice, minRating);
    }
}
