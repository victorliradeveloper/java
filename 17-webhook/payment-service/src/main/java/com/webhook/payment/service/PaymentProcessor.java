package com.webhook.payment.service;

import com.webhook.payment.client.OrderServiceClient;
import com.webhook.payment.domain.Payment;
import com.webhook.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);
    private static final Duration PROCESSING_DELAY = Duration.ofSeconds(30);

    private final PaymentService paymentService;
    private final OrderServiceClient orderServiceClient;

    public PaymentProcessor(PaymentService paymentService, OrderServiceClient orderServiceClient) {
        this.paymentService = paymentService;
        this.orderServiceClient = orderServiceClient;
    }

    @Async
    public void processAsync(UUID paymentId) {
        log.info("Processing payment {} (will sleep {}s)", paymentId, PROCESSING_DELAY.toSeconds());
        try {
            Thread.sleep(PROCESSING_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processing interrupted for payment {}", paymentId);
            return;
        }
        Payment approved = paymentService.updateStatus(paymentId, PaymentStatus.APPROVED);
        UUID eventId = UUID.randomUUID();
        log.info("Payment {} approved, notifying order service (event {})", paymentId, eventId);
        orderServiceClient.notifyPayment(eventId, approved.orderId(), approved.status());
    }
}
