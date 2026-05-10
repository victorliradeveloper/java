package com.ecommerce.order.domain.model;

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
