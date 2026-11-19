package com.studies.sqlqueries.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.*;

// -------------------------------------------------------
// Tables produced by this entity:
//
//   products
//     id | name | description | price | stock_quantity | category | created_at
//
//   product_tags        (one row per tag)
//     product_id | tag
//
//   product_attributes  (one row per attribute key-value pair)
//     product_id | attr_key | attr_value
//
//   product_ratings     (one row per rating)
//     product_id | user_id | score | comment | rated_at
//
// MongoDB equivalent: @Document(collection = "products") with
// embedded tags[], attributes{}, and ratings[] fields.
// -------------------------------------------------------
@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_category", columnList = "category"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String description;
    private Double price;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    private String category;

    // product_tags: product_id | tag
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    // product_attributes: product_id | attr_key | attr_value
    // @MapKeyColumn defines the column for the Map key.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value")
    private Map<String, String> attributes = new HashMap<>();

    // product_ratings: product_id | user_id | score | comment | rated_at
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_ratings", joinColumns = @JoinColumn(name = "product_id"))
    private List<Rating> ratings = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId()   { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName()  { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getPrice()   { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    public List<Rating> getRatings() { return ratings; }
    public void setRatings(List<Rating> ratings) { this.ratings = ratings; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
