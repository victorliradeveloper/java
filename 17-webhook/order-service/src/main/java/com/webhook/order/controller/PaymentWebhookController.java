package com.webhook.order.controller;

import com.webhook.order.dto.request.PaymentWebhookRequest;
import com.webhook.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/payment")
public class PaymentWebhookController {

    private final OrderService orderService;

    public PaymentWebhookController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody PaymentWebhookRequest request) {
        orderService.processPaymentWebhook(request.eventId(), request.orderId(), request.status());
        return ResponseEntity.noContent().build();
    }
}
