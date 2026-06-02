package com.webhook.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    public Order(UUID id, String product, BigDecimal amount, OrderStatus status) {
        this.id = id;
        this.product = product;
        this.amount = amount;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    public String product() {
        return product;
    }

    public BigDecimal amount() {
        return amount;
    }

    public OrderStatus status() {
        return status;
    }

    public void changeStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
