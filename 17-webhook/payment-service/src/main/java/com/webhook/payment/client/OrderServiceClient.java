package com.webhook.payment.client;

import com.webhook.payment.client.dto.PaymentWebhookPayload;
import com.webhook.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void notifyPayment(UUID eventId, UUID orderId, PaymentStatus status) {
        restClient.post()
                .uri("/webhooks/payment")
                .body(new PaymentWebhookPayload(eventId, orderId, status.name()))
                .retrieve()
                .toBodilessEntity();
    }
}
