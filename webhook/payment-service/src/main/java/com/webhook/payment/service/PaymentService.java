package com.webhook.payment.service;

import com.webhook.payment.domain.Payment;
import com.webhook.payment.domain.PaymentStatus;
import com.webhook.payment.dto.request.CreatePaymentRequest;
import com.webhook.payment.repository.PaymentRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentProcessor processor;

    public PaymentService(PaymentRepository repository, @Lazy PaymentProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    @Transactional
    public Payment create(CreatePaymentRequest request) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                request.orderId(),
                request.amount(),
                PaymentStatus.PROCESSING
        );
        repository.save(payment);
        processor.processAsync(payment.id());
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional
    public Payment updateStatus(UUID id, PaymentStatus newStatus) {
        Payment payment = findById(id);
        payment.changeStatus(newStatus);
        return repository.save(payment);
    }
}
