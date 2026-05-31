package com.ecommerce.user.infrastructure.adapter.out.messaging.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
