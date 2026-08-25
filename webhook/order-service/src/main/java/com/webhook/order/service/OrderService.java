package com.webhook.order.service;

import com.webhook.order.client.PaymentClient;
import com.webhook.order.domain.Order;
import com.webhook.order.domain.OrderStatus;
import com.webhook.order.domain.ProcessedWebhookEvent;
import com.webhook.order.dto.request.CreateOrderRequest;
import com.webhook.order.repository.OrderRepository;
import com.webhook.order.repository.ProcessedWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final ProcessedWebhookEventRepository processedEventRepository;
    private final PaymentClient paymentClient;

    public OrderService(
            OrderRepository repository,
            ProcessedWebhookEventRepository processedEventRepository,
            PaymentClient paymentClient
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = new Order(
                UUID.randomUUID(),
                request.product(),
                request.amount(),
                OrderStatus.PENDING
        );
        repository.save(order);

        paymentClient.createPayment(order.id(), order.amount());

        order.changeStatus(OrderStatus.PROCESSING);
        return repository.save(order);
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public void processPaymentWebhook(UUID eventId, UUID orderId, OrderStatus newStatus) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }
        Order order = findById(orderId);
        order.changeStatus(newStatus);
        repository.save(order);
        processedEventRepository.save(new ProcessedWebhookEvent(eventId, Instant.now()));
        log.info("Order {} updated to {} (event {})", orderId, newStatus, eventId);
    }
}
