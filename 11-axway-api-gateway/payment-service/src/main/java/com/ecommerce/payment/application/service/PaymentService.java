package com.ecommerce.payment.application.service;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.event.OrderCreatedEvent;
import com.ecommerce.payment.domain.model.event.PaymentResultEvent;
import com.ecommerce.payment.domain.port.in.FindPaymentsUseCase;
import com.ecommerce.payment.domain.port.in.ProcessOrderPaymentUseCase;
import com.ecommerce.payment.domain.port.out.PaymentEventPublisher;
import com.ecommerce.payment.domain.port.out.PaymentGateway;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements ProcessOrderPaymentUseCase, FindPaymentsUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.warn("Payment already exists for order: {}", event.orderId());
            return;
        }

        var payment = Payment.newProcessing(
                event.orderId(),
                event.userId(),
                event.totalAmount(),
                event.currency()
        );
        var saved = paymentRepository.save(payment);

        try {
            var intent = paymentGateway.createPaymentIntent(
                    event.totalAmount(), event.currency(), event.orderId()
            );
            saved.markPending(intent);
            var persisted = paymentRepository.save(saved);

            paymentEventPublisher.publishProcessed(PaymentResultEvent.of(persisted, "PENDING", null));
            log.info("PaymentIntent created for order {}: {}", event.orderId(), intent.id());

        } catch (Exception e) {
            log.error("Gateway error for order {}: {}", event.orderId(), e.getMessage());
            saved.markFailed(e.getMessage());
            var persisted = paymentRepository.save(saved);

            paymentEventPublisher.publishFailed(PaymentResultEvent.of(persisted, "FAILED", e.getMessage()));
        }
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Override
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }
}
