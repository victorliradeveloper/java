package com.webhook.order.client;

import com.webhook.order.client.dto.PaymentCreationRequest;
import com.webhook.order.client.dto.PaymentCreationResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public PaymentCreationResponse createPayment(UUID orderId, BigDecimal amount) {
        return restClient.post()
                .uri("/payments")
                .body(new PaymentCreationRequest(orderId, amount))
                .retrieve()
                .body(PaymentCreationResponse.class);
    }
}
