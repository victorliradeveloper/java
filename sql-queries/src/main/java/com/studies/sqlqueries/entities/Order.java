package com.studies.sqlqueries.entities;

import com.studies.sqlqueries.enums.OrderStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// -------------------------------------------------------
// Tables produced by this entity:
//
//   orders
//     id | customer_id | shipping_street | shipping_city | shipping_state
//        | shipping_zip_code | shipping_country
//        | status | total_amount | created_at | updated_at
//
//   order_items  (one row per item)
//     order_id | product_id | product_name | unit_price | quantity
//
// MongoDB equivalent: @Document(collection = "orders") with
//   @DBRef for customer → SQL @ManyToOne FK
//   embedded items[]    → SQL @ElementCollection (order_items table)
//   embedded address    → SQL @Embedded (columns in the orders table itself)
// -------------------------------------------------------
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne stores customer_id as a FK in the orders table.
    // MongoDB stored a @DBRef (a document reference by _id).
    // In SQL the FK is a direct column: orders.customer_id → customers.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // order_items: order_id | product_id | product_name | unit_price | quantity
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    // @Embedded: address fields are stored directly in the orders table with a prefix.
    // This is the SQL equivalent of an embedded sub-document in MongoDB.
    // Compare with Customer.addresses (@ElementCollection) which uses a separate table
    // because a customer can have MULTIPLE addresses.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street",  column = @Column(name = "shipping_street")),
            @AttributeOverride(name = "city",    column = @Column(name = "shipping_city")),
            @AttributeOverride(name = "state",   column = @Column(name = "shipping_state")),
            @AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip_code")),
            @AttributeOverride(name = "country", column = @Column(name = "shipping_country"))
    })
    private Address shippingAddress;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()   { return id; }
    public void setId(Long id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
