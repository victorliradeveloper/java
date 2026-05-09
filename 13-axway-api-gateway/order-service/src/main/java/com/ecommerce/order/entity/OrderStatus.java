package com.ecommerce.order.entity;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
