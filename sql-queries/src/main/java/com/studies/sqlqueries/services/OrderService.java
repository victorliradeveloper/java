package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Order;
import com.studies.sqlqueries.enums.OrderStatus;
import com.studies.sqlqueries.repositories.OrderRepository;
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

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> findByCustomerId(Long customerId) {
        return orderRepository.findByCustomer_Id(customerId);
    }

    public List<Order> findByCustomerAndStatus(Long customerId, OrderStatus status) {
        return orderRepository.findByCustomer_IdAndStatus(customerId, status);
    }

    public List<Order> findByMinTotal(Double amount) {
        return orderRepository.findByMinimumTotal(amount);
    }

    public List<Order> findByShippingCity(String city) {
        return orderRepository.findByShippingCity(city);
    }

    public List<Order> findByShippingCityAndStatusIn(String city, List<OrderStatus> statuses) {
        return orderRepository.findByShippingCityAndStatusIn(city, statuses);
    }

    public List<Order> findByStatusAndDateRange(OrderStatus status, LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByStatusAndCreatedAtBetween(status, start, end);
    }

    public List<Order> findOrdersContainingProduct(Long productId) {
        return orderRepository.findOrdersContainingProduct(productId);
    }

    public List<Order> findByStatusWithMinItemQuantity(OrderStatus status, int minQuantity) {
        return orderRepository.findByStatusWithMinItemQuantity(status, minQuantity);
    }

    public List<Order> findStaleOrders(int pendingDaysAgo, int processingDaysAgo) {
        var now = LocalDateTime.now();
        return orderRepository.findStaleOrders(
                now.minusDays(pendingDaysAgo),
                now.minusDays(processingDaysAgo)
        );
    }

    public List<Order> findActiveOrdersWithExpensiveItems(Double minUnitPrice) {
        return orderRepository.findActiveOrdersWithExpensiveItems(minUnitPrice);
    }

    public List<Order> findLargeActiveOrders(Double minTotal, int minItems) {
        return orderRepository.findLargeActiveOrders(minTotal, minItems);
    }

    public Order updateStatus(Long id, OrderStatus newStatus) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }
}
