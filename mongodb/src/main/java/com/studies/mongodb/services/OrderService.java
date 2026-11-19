package com.studies.mongodb.services;

import com.studies.mongodb.documents.OrderDocument;
import com.studies.mongodb.enums.OrderStatus;
import com.studies.mongodb.repositories.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderDocument> findAll() {
        return orderRepository.findAll();
    }

    public Optional<OrderDocument> findById(String id) {
        return orderRepository.findById(id);
    }

    public List<OrderDocument> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<OrderDocument> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<OrderDocument> findByCustomerAndStatus(String customerId, OrderStatus status) {
        return orderRepository.findByCustomerIdAndStatus(customerId, status);
    }

    public List<OrderDocument> findByMinTotal(Double amount) {
        return orderRepository.findByMinimumTotal(amount);
    }

    public List<OrderDocument> findByShippingCity(String city) {
        return orderRepository.findByShippingCity(city);
    }

    public List<OrderDocument> findByShippingCityAndStatusIn(String city, List<String> statuses) {
        return orderRepository.findByShippingCityAndStatusIn(city, statuses);
    }

    public List<OrderDocument> findByStatusAndDateRange(OrderStatus status, LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByStatusAndCreatedAtBetween(status, start, end);
    }

    public List<OrderDocument> findOrdersContainingProduct(String productId) {
        return orderRepository.findOrdersContainingProduct(productId);
    }

    public List<OrderDocument> findByStatusWithMinItemQuantity(String status, int minQuantity) {
        return orderRepository.findByStatusWithMinItemQuantity(status, minQuantity);
    }

    public List<OrderDocument> findStaleOrders(int pendingDaysAgo, int processingDaysAgo) {
        var now = LocalDateTime.now();
        return orderRepository.findStaleOrders(
                now.minusDays(pendingDaysAgo),
                now.minusDays(processingDaysAgo)
        );
    }

    public List<OrderDocument> findActiveOrdersWithExpensiveItems(Double minUnitPrice) {
        return orderRepository.findActiveOrdersWithExpensiveItems(minUnitPrice);
    }

    public List<OrderDocument> findLargeActiveOrders(Double minTotal, int minItems) {
        return orderRepository.findLargeActiveOrders(minTotal, minItems);
    }

    public OrderDocument updateStatus(String id, OrderStatus newStatus) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }
}
