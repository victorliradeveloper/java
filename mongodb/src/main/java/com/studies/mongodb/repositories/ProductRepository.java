package com.studies.mongodb.repositories;

import com.studies.mongodb.documents.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {

    // ==================== SIMPLE ====================

    List<ProductDocument> findByCategory(String category);

    List<ProductDocument> findByPriceBetween(Double minPrice, Double maxPrice);

    List<ProductDocument> findByStockQuantityLessThan(int quantity);

    List<ProductDocument> findByStockQuantityGreaterThan(int quantity);

    // ==================== MEDIUM ====================

    List<ProductDocument> findByTagsContaining(String tag);

    List<ProductDocument> findByCategoryAndPriceLessThanEqual(String category, Double maxPrice);

    List<ProductDocument> findByCategoryAndStockQuantityGreaterThan(String category, int minStock);

    // Products that have ALL specified tags (not just any)
    @Query("{ 'tags': { $all: ?0 } }")
    List<ProductDocument> findByAllTags(List<String> tags);

    // Products matching a specific attribute (e.g. color=black)
    @Query("{ 'attributes.color': ?0 }")
    List<ProductDocument> findByColor(String color);

    // In-stock products that have at least one tag from the given list
    @Query("{ 'tags': { $in: ?0 }, 'stockQuantity': { $gt: 0 } }")
    List<ProductDocument> findInStockByAnyTag(List<String> tags);

    // Products within price range that belong to one of the given categories
    @Query("{ 'category': { $in: ?0 }, 'price': { $gte: ?1, $lte: ?2 } }")
    List<ProductDocument> findByCategoriesAndPriceRange(List<String> categories, Double minPrice, Double maxPrice);

    // ==================== COMPLEX ====================

    // Products that a specific user has rated ($elemMatch on embedded array)
    @Query("{ 'ratings': { $elemMatch: { 'userId': ?0 } } }")
    List<ProductDocument> findRatedByUser(String userId);

    // Products where at least one rating is >= minScore
    @Query("{ 'ratings': { $elemMatch: { 'score': { $gte: ?0 } } } }")
    List<ProductDocument> findByMinimumRatingScore(int minScore);

    // Products where ALL ratings are >= minScore (none below threshold)
    // Uses $not + $elemMatch: returns docs where NO element matches the negative condition
    @Query("{ 'ratings': { $not: { $elemMatch: { 'score': { $lt: ?0 } } } }, 'ratings.0': { $exists: true } }")
    List<ProductDocument> findWithOnlyHighRatings(int minScore);

    // Three-way filter: category + at least one rating >= minRating + price <= maxPrice
    @Query("""
            { $and: [
                { 'category': ?0 },
                { 'ratings': { $elemMatch: { 'score': { $gte: ?1 } } } },
                { 'price': { $lte: ?2 } }
            ] }
            """)
    List<ProductDocument> findByCategoryWithMinRatingAndMaxPrice(String category, int minRating, Double maxPrice);

    // Products that belong to a category OR have a specific tag
    @Query("""
            { $or: [
                { 'category': ?0 },
                { 'tags': ?1 }
            ] }
            """)
    List<ProductDocument> findByCategoryOrTag(String category, String tag);

    // "Deals": in stock, below price ceiling, with at least one good rating
    @Query("""
            { $and: [
                { 'category': ?0 },
                { 'price': { $lt: ?1 } },
                { 'stockQuantity': { $gt: 0 } },
                { 'ratings': { $elemMatch: { 'score': { $gte: ?2 } } } }
            ] }
            """)
    List<ProductDocument> findCategoryDeals(String category, Double maxPrice, int minRating);
}
