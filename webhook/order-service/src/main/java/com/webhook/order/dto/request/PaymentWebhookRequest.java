package com.webhook.order.dto.request;

import com.webhook.order.domain.OrderStatus;

import java.util.UUID;

public record PaymentWebhookRequest(
        UUID eventId,
        UUID orderId,
        OrderStatus status
) {}
