package com.webhook.payment.client.dto;

import java.util.UUID;

public record PaymentWebhookPayload(
        UUID eventId,
        UUID orderId,
        String status
) {}
