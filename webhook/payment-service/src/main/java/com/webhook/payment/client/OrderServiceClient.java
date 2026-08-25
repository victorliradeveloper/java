package com.webhook.payment.client;

import com.webhook.payment.client.dto.PaymentWebhookPayload;
import com.webhook.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final RestClient restClient;

    public OrderServiceClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Retryable(
            retryFor = RestClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void notifyPayment(UUID eventId, UUID orderId, PaymentStatus status) {
        restClient.post()
                .uri("/webhooks/payment")
                .body(new PaymentWebhookPayload(eventId, orderId, status.name()))
                .retrieve()
                .toBodilessEntity();
    }

    @Recover
    public void recover(RestClientException ex, UUID eventId, UUID orderId, PaymentStatus status) {
        log.error("Failed to deliver webhook after retries: eventId={}, orderId={}, status={}",
                eventId, orderId, status, ex);
    }
}
