package com.studies.sqlqueries.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// -------------------------------------------------------
// @Embeddable: each item is stored as a row in the
// "order_items" table (via @ElementCollection on Order).
//
// Table schema:
//   order_id | product_id | product_name | unit_price | quantity
//
// MongoDB equivalent: an element of the embedded items array.
// -------------------------------------------------------
@Embeddable
public class OrderItem {

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit_price")
    private Double unitPrice;

    private Integer quantity;

    public OrderItem() {}

    public OrderItem(Long productId, String productName, Double unitPrice, Integer quantity) {
        this.productId   = productId;
        this.productName = productName;
        this.unitPrice   = unitPrice;
        this.quantity    = quantity;
    }

    public Long getProductId()       { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName()   { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Double getUnitPrice()     { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public Integer getQuantity()     { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
