package com.studies.sqlqueries.controllers;

import com.studies.sqlqueries.entities.Order;
import com.studies.sqlqueries.enums.OrderStatus;
import com.studies.sqlqueries.services.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> findById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/status/{status}")
    public List<Order> findByStatus(@PathVariable OrderStatus status) {
        return orderService.findByStatus(status);
    }

    // SQL: SELECT * FROM orders WHERE customer_id = ?
    @GetMapping("/customer/{customerId}")
    public List<Order> findByCustomer(@PathVariable Long customerId) {
        return orderService.findByCustomerId(customerId);
    }

    // ==================== MEDIUM ====================

    @GetMapping("/customer/{customerId}/status/{status}")
    public List<Order> findByCustomerAndStatus(
            @PathVariable Long customerId,
            @PathVariable OrderStatus status) {
        return orderService.findByCustomerAndStatus(customerId, status);
    }

    // SQL: WHERE total_amount >= ?
    @GetMapping("/min-total")
    public List<Order> findByMinTotal(@RequestParam Double amount) {
        return orderService.findByMinTotal(amount);
    }

    // SQL: WHERE status = ? AND created_at BETWEEN ? AND ?
    // ?status=DELIVERED&start=2024-01-01T00:00:00&end=2024-12-31T23:59:59
    @GetMapping("/date-range")
    public List<Order> findByStatusAndDateRange(
            @RequestParam OrderStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return orderService.findByStatusAndDateRange(status, start, end);
    }

    // SQL: WHERE shipping_city = ?  (column in the orders table itself — no join)
    @GetMapping("/shipping/city/{city}")
    public List<Order> findByShippingCity(@PathVariable String city) {
        return orderService.findByShippingCity(city);
    }

    // SQL: WHERE shipping_city = ? AND status IN (?, ...)
    // ?statuses=PENDING&statuses=PROCESSING
    @GetMapping("/shipping/city/{city}/statuses")
    public List<Order> findByShippingCityAndStatusIn(
            @PathVariable String city,
            @RequestParam List<OrderStatus> statuses) {
        return orderService.findByShippingCityAndStatusIn(city, statuses);
    }

    // ==================== COMPLEX ====================

    // SQL: JOIN order_items WHERE product_id = ?
    @GetMapping("/product/{productId}")
    public List<Order> findOrdersContainingProduct(@PathVariable Long productId) {
        return orderService.findOrdersContainingProduct(productId);
    }

    // SQL: JOIN order_items + WHERE status=? AND quantity>=?
    // ?status=DELIVERED&minQuantity=2
    @GetMapping("/status-qty")
    public List<Order> findByStatusWithMinItemQuantity(
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "2") int minQuantity) {
        return orderService.findByStatusWithMinItemQuantity(status, minQuantity);
    }

    // SQL OR: (status='PENDING' AND created_at < ?) OR (status='PROCESSING' AND created_at < ?)
    @GetMapping("/stale")
    public List<Order> findStaleOrders(
            @RequestParam(defaultValue = "7") int pendingDays,
            @RequestParam(defaultValue = "14") int processingDays) {
        return orderService.findStaleOrders(pendingDays, processingDays);
    }

    // SQL: JOIN order_items WHERE unit_price >= ? AND status <> 'CANCELLED'
    @GetMapping("/expensive-items")
    public List<Order> findActiveOrdersWithExpensiveItems(@RequestParam Double minUnitPrice) {
        return orderService.findActiveOrdersWithExpensiveItems(minUnitPrice);
    }

    // SQL: total_amount >= ? AND status NOT IN (...) AND item count subquery >= ?
    @GetMapping("/large")
    public List<Order> findLargeActiveOrders(
            @RequestParam Double minTotal,
            @RequestParam(defaultValue = "2") int minItems) {
        return orderService.findLargeActiveOrders(minTotal, minItems);
    }

    // ==================== UPDATE ====================

    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }
}
