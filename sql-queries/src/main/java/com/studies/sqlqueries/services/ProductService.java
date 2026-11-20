package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Product;
import com.studies.sqlqueries.entities.Rating;
import com.studies.sqlqueries.repositories.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
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

    // ==================== BASIC UPDATE ====================

    // save() — JPA: UPDATE products SET name=?, price=?, ... WHERE id=?
    // MongoDB: fullReplace via repository.save()
    @Transactional
    public Product fullReplace(Long id, Product updated) {
        var existing = findByIdOrThrow(id);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        return productRepository.save(updated);
    }

    // @Modifying @Query → UPDATE products SET price = ? WHERE id = ?
    // MongoDB: $set no campo price
    @Transactional
    public Product updatePrice(Long id, Double price) {
        findByIdOrThrow(id);
        productRepository.updatePrice(id, price);
        return findByIdOrThrow(id);
    }

    // ==================== MEDIUM UPDATE ====================

    // @Modifying @Query → UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?
    // MongoDB: $inc — positivo = entrada, negativo = saída
    @Transactional
    public Product adjustStock(Long id, int delta) {
        findByIdOrThrow(id);
        productRepository.adjustStock(id, delta);
        return findByIdOrThrow(id);
    }

    // $addToSet equivalente: carrega, verifica duplicata e adiciona se não existir.
    // SQL não tem $addToSet nativo; a deduplicação fica na aplicação.
    // Alternativa: usar Set<String> na entidade em vez de List<String>.
    @Transactional
    public Product addTag(Long id, String tag) {
        var product = findByIdOrThrow(id);
        if (!product.getTags().contains(tag)) {
            product.getTags().add(tag);
            return productRepository.save(product);
        }
        return product;
    }

    // $pull equivalente: carrega, remove da lista e salva.
    // JPA recria todas as linhas de product_tags para este produto.
    @Transactional
    public Product removeTag(Long id, String tag) {
        var product = findByIdOrThrow(id);
        product.getTags().remove(tag);
        return productRepository.save(product);
    }

    // $push com $sort equivalente: carrega, adiciona o Rating, ordena em memória e salva.
    // JPA recria todas as linhas de product_ratings — sem $sort nativo no banco.
    @Transactional
    public Product addRating(Long id, Rating rating) {
        var product = findByIdOrThrow(id);
        product.getRatings().add(rating);
        product.getRatings().sort(Comparator.comparingInt(Rating::getScore).reversed());
        return productRepository.save(product);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti + $mul equivalente: @Modifying @Query → UPDATE SET price = price * ? WHERE category = ?
    // Uma única query SQL atualiza todos os produtos da categoria — sem loop na aplicação.
    @Transactional
    public int adjustCategoryPrice(String category, double factor) {
        return productRepository.adjustCategoryPrice(category, factor);
    }

    // $pull com condição equivalente: carrega, remove o Rating pelo userId e salva.
    @Transactional
    public Product removeRating(Long id, Long userId) {
        var product = findByIdOrThrow(id);
        product.getRatings().removeIf(r -> r.getUserId().equals(userId));
        return productRepository.save(product);
    }

    private Product findByIdOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
