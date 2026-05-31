package com.ecommerce.order.domain.exception;

public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(Long id) {
        super("Access denied to order: " + id);
    }
}
