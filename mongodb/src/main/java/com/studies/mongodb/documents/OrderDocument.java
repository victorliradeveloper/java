package com.studies.mongodb.documents;

import com.studies.mongodb.documents.embedded.AddressDocument;
import com.studies.mongodb.documents.embedded.OrderItemDocument;
import com.studies.mongodb.enums.OrderStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "orders")
public class OrderDocument {

    @Id
    private String id;

    @DBRef
    private CustomerDocument customer;

    private List<OrderItemDocument> items;
    private AddressDocument shippingAddress;
    private OrderStatus status;
    private Double totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public CustomerDocument getCustomer() { return customer; }
    public void setCustomer(CustomerDocument customer) { this.customer = customer; }

    public List<OrderItemDocument> getItems() { return items; }
    public void setItems(List<OrderItemDocument> items) { this.items = items; }

    public AddressDocument getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(AddressDocument shippingAddress) { this.shippingAddress = shippingAddress; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
