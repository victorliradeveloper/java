package com.studies.sqlqueries.repositories;

import com.studies.sqlqueries.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ==================== SIMPLE ====================

    // SQL: SELECT * FROM products WHERE category = ?
    List<Product> findByCategory(String category);

    // SQL: SELECT * FROM products WHERE price BETWEEN ? AND ?
    List<Product> findByPriceBetween(Double minPrice, Double maxPrice);

    // SQL: SELECT * FROM products WHERE stock_quantity < ?
    List<Product> findByStockQuantityLessThan(int quantity);

    // SQL: SELECT * FROM products WHERE stock_quantity > ?
    List<Product> findByStockQuantityGreaterThan(int quantity);

    // ==================== MEDIUM ====================

    // JOIN to product_tags — equivalent to MongoDB checking if a value exists in an array.
    // MongoDB: "findByTagsContaining" via Spring Data → { tags: 'value' }
    // SQL JPQL: JOIN on the product_tags collection table, then filter the tag value.
    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t = :tag")
    List<Product> findByTagsContaining(@Param("tag") String tag);

    // SQL: SELECT * FROM products WHERE category = ? AND price <= ?
    List<Product> findByCategoryAndPriceLessThanEqual(String category, Double maxPrice);

    // SQL: SELECT * FROM products WHERE category = ? AND stock_quantity > ?
    List<Product> findByCategoryAndStockQuantityGreaterThan(String category, int minStock);

    // ALL specified tags must be present — equivalent to MongoDB $all.
    // MongoDB: { 'tags': { $all: ?0 } }
    // SQL: count how many of the requested tags actually exist for this product;
    //   if that count equals the total number of tags requested, all are present.
    // :#{#tags.size()} uses Spring Data SpEL to inject the list size at runtime.
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT p.* FROM products p
            WHERE (
                SELECT COUNT(*) FROM product_tags pt
                WHERE pt.product_id = p.id AND pt.tag IN (:tags)
            ) = :#{#tags.size()}
            """)
    List<Product> findByAllTags(@Param("tags") List<String> tags);

    // Map key-value lookup via a JOIN to the product_attributes table.
    // MongoDB: { 'attributes.color': ?0 }  (dot-notation into a sub-document)
    // SQL: JOIN product_attributes WHERE attr_key = 'color' AND attr_value = ?
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT p.* FROM products p
            JOIN product_attributes pa ON pa.product_id = p.id
            WHERE pa.attr_key = 'color' AND pa.attr_value = :color
            """)
    List<Product> findByColor(@Param("color") String color);

    // IN clause on a joined collection — equivalent to MongoDB $in on an array.
    // MongoDB: { 'tags': { $in: ?0 }, 'stockQuantity': { $gt: 0 } }
    // SQL: JOIN product_tags WHERE tag IN (:tags) AND stock_quantity > 0
    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t IN :tags AND p.stockQuantity > 0")
    List<Product> findInStockByAnyTag(@Param("tags") List<String> tags);

    // IN on the main table column + BETWEEN for price range.
    // MongoDB: { 'category': { $in: ?0 }, 'price': { $gte: ?1, $lte: ?2 } }
    // SQL: WHERE category IN (:categories) AND price BETWEEN :min AND :max
    @Query("SELECT p FROM Product p WHERE p.category IN :categories AND p.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findByCategoriesAndPriceRange(@Param("categories") List<String> categories,
                                                @Param("minPrice") Double minPrice,
                                                @Param("maxPrice") Double maxPrice);

    // ==================== COMPLEX ====================

    // JOIN to product_ratings to find products rated by a specific user.
    // MongoDB: { 'ratings': { $elemMatch: { 'userId': ?0 } } }
    // SQL: JOIN on the ratings collection, filter by user_id column.
    @Query("SELECT DISTINCT p FROM Product p JOIN p.ratings r WHERE r.userId = :userId")
    List<Product> findRatedByUser(@Param("userId") Long userId);

    // JOIN to find products where AT LEAST ONE rating meets the threshold.
    // MongoDB: { 'ratings': { $elemMatch: { 'score': { $gte: ?0 } } } }
    // SQL: JOIN on ratings, WHERE score >= :minScore — the JOIN guarantees at least one match.
    @Query("SELECT DISTINCT p FROM Product p JOIN p.ratings r WHERE r.score >= :minScore")
    List<Product> findByMinimumRatingScore(@Param("minScore") int minScore);

    // NOT EXISTS ensures NO rating falls below the threshold (all ratings are good).
    // MongoDB: { 'ratings': { $not: { $elemMatch: { 'score': { $lt: ?0 } } } }, 'ratings.0': { $exists: true } }
    //   → $not + $elemMatch inverts the match; 'ratings.0' ensures the array is non-empty.
    // SQL: NOT EXISTS (bad rating subquery) AND EXISTS (at least one rating subquery)
    @Query(nativeQuery = true, value = """
            SELECT p.* FROM products p
            WHERE NOT EXISTS (
                SELECT 1 FROM product_ratings pr
                WHERE pr.product_id = p.id AND pr.score < :minScore
            )
            AND EXISTS (
                SELECT 1 FROM product_ratings pr2
                WHERE pr2.product_id = p.id
            )
            """)
    List<Product> findWithOnlyHighRatings(@Param("minScore") int minScore);

    // Three-way filter combining category, rating threshold, and price ceiling.
    // MongoDB: { $and: [ { category:?0 }, { ratings:{$elemMatch:{score:{$gte:?1}}} }, { price:{$lte:?2} } ] }
    // SQL: AND clauses on the main table + JOIN to ratings for the score condition.
    @Query("""
            SELECT DISTINCT p FROM Product p JOIN p.ratings r
            WHERE p.category = :category
              AND r.score >= :minRating
              AND p.price <= :maxPrice
            """)
    List<Product> findByCategoryWithMinRatingAndMaxPrice(@Param("category") String category,
                                                         @Param("minRating") int minRating,
                                                         @Param("maxPrice") Double maxPrice);

    // OR between two different columns (category OR tag membership).
    // MongoDB: { $or: [ { 'category': ?0 }, { 'tags': ?1 } ] }
    // SQL: LEFT JOIN so products without any tag are still returned when matching by category.
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN p.tags t WHERE p.category = :category OR t = :tag")
    List<Product> findByCategoryOrTag(@Param("category") String category, @Param("tag") String tag);

    // "Deals": four simultaneous conditions — category + price ceiling + in stock + good rating.
    // MongoDB: { $and: [ { category:?0 }, { price:{$lt:?1} }, { stockQuantity:{$gt:0} }, { ratings:{$elemMatch:{score:{$gte:?2}}} } ] }
    // SQL: AND on main columns + JOIN to ratings ensures at least one rating meets the score.
    @Query("""
            SELECT DISTINCT p FROM Product p JOIN p.ratings r
            WHERE p.category = :category
              AND p.price < :maxPrice
              AND p.stockQuantity > 0
              AND r.score >= :minRating
            """)
    List<Product> findCategoryDeals(@Param("category") String category,
                                    @Param("maxPrice") Double maxPrice,
                                    @Param("minRating") int minRating);
}
