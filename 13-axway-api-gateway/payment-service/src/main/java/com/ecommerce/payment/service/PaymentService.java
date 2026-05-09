package com.ecommerce.payment.service;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.event.OrderCreatedEvent;
import com.ecommerce.payment.event.PaymentResultEvent;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.payment-processed.routing-key}")
    private String paymentProcessedRoutingKey;

    @Value("${rabbitmq.payment-failed.routing-key}")
    private String paymentFailedRoutingKey;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.warn("Payment already exists for order: {}", event.orderId());
            return;
        }

        var payment = Payment.builder()
                .orderId(event.orderId())
                .userId(event.userId())
                .amount(event.totalAmount())
                .currency(event.currency())
                .status(PaymentStatus.PROCESSING)
                .build();

        paymentRepository.save(payment);

        try {
            var intent = stripeService.createPaymentIntent(event.totalAmount(), event.currency(), event.orderId());

            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);

            publishResult(payment, "PENDING", null);
            log.info("PaymentIntent created for order {}: {}", event.orderId(), intent.getId());

        } catch (Exception e) {
            log.error("Stripe error for order {}: {}", event.orderId(), e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);

            publishResult(payment, "FAILED", e.getMessage());
        }
    }

    private void publishResult(Payment payment, String status, String failureReason) {
        var event = new PaymentResultEvent(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getAmount(),
                payment.getCurrency(),
                status,
                failureReason
        );

        String routingKey = "FAILED".equals(status) ? paymentFailedRoutingKey : paymentProcessedRoutingKey;
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
