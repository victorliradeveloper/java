package com.studies.mongodb.services;

import com.studies.mongodb.documents.ProductDocument;
import com.studies.mongodb.documents.embedded.RatingDocument;
import com.studies.mongodb.repositories.ProductRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
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

    // ==================== BASIC UPDATE ====================

    // save() — substitui o documento inteiro; campos ausentes tornam-se null
    public ProductDocument fullReplace(String id, ProductDocument updated) {
        var existing = findByIdOrThrow(id);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        return productRepository.save(updated);
    }

    // $set — atualiza apenas o preço sem tocar nos outros campos
    public ProductDocument updatePrice(String id, Double price) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().set("price", price);
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    // ==================== MEDIUM UPDATE ====================

    // $inc — ajusta o estoque atomicamente (positivo = entrada, negativo = saída)
    public ProductDocument adjustStock(String id, int delta) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().inc("stockQuantity", delta);
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    // $addToSet — adiciona ao array SOMENTE se o elemento ainda não existir
    // Diferença do $push: $push permite duplicatas, $addToSet não
    public ProductDocument addTag(String id, String tag) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().addToSet("tags", tag);
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    // $pull — remove todas as ocorrências do valor no array
    public ProductDocument removeTag(String id, String tag) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().pull("tags", tag);
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    // $push com $sort — adiciona ao array e mantém ordenado por score descendente
    // O MongoDB reordena o array inteiro após cada inserção
    public ProductDocument addRating(String id, RatingDocument rating) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update();
        update.push("ratings")
                .sort(Sort.by(Sort.Direction.DESC, "score"))
                .each(rating);
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti + $mul — multiplica o preço de TODOS os produtos da categoria
    // $mul é diferente de $inc: multiplica pelo fator (1.1 = +10%, 0.9 = -10%)
    public long adjustCategoryPrice(String category, double factor) {
        var query = Query.query(Criteria.where("category").is(category));
        var update = new Update().multiply("price", factor);
        return mongoTemplate.updateMulti(query, update, ProductDocument.class).getModifiedCount();
    }

    // $pull com condição — remove um subdocumento do array pelo campo userId
    // Diferente de remover string simples: precisa passar um Document como filtro
    public ProductDocument removeRating(String id, String userId) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().pull("ratings", new Document("userId", userId));
        mongoTemplate.updateFirst(query, update, ProductDocument.class);
        return findByIdOrThrow(id);
    }

    private ProductDocument findByIdOrThrow(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
