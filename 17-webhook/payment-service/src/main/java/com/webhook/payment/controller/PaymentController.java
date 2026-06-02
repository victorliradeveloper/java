package com.webhook.payment.controller;

import com.webhook.payment.dto.request.CreatePaymentRequest;
import com.webhook.payment.dto.response.PaymentResponse;
import com.webhook.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestBody CreatePaymentRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        PaymentResponse response = PaymentResponse.from(paymentService.create(request));
        URI location = uriBuilder.path("/payments/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.accepted().location(location).body(response);
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.findById(id));
    }
}
