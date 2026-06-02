package com.webhook.order.client.dto;

import java.util.UUID;

public record PaymentCreationResponse(
        UUID id,
        String status
) {}
