package com.studies.mongodb.controllers;

import com.studies.mongodb.documents.OrderDocument;
import com.studies.mongodb.documents.embedded.OrderItemDocument;
import com.studies.mongodb.enums.OrderStatus;
import com.studies.mongodb.services.OrderService;
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
    public List<OrderDocument> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDocument> findById(@PathVariable String id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/status/{status}")
    public List<OrderDocument> findByStatus(@PathVariable OrderStatus status) {
        return orderService.findByStatus(status);
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderDocument> findByCustomer(@PathVariable String customerId) {
        return orderService.findByCustomerId(customerId);
    }

    // ==================== MEDIUM ====================

    @GetMapping("/customer/{customerId}/status/{status}")
    public List<OrderDocument> findByCustomerAndStatus(
            @PathVariable String customerId,
            @PathVariable OrderStatus status) {
        return orderService.findByCustomerAndStatus(customerId, status);
    }

    @GetMapping("/min-total")
    public List<OrderDocument> findByMinTotal(@RequestParam Double amount) {
        return orderService.findByMinTotal(amount);
    }

    // Date range: ?status=DELIVERED&start=2024-01-01T00:00:00&end=2024-12-31T23:59:59
    @GetMapping("/date-range")
    public List<OrderDocument> findByStatusAndDateRange(
            @RequestParam OrderStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return orderService.findByStatusAndDateRange(status, start, end);
    }

    @GetMapping("/shipping/city/{city}")
    public List<OrderDocument> findByShippingCity(@PathVariable String city) {
        return orderService.findByShippingCity(city);
    }

    // City + multiple statuses: ?statuses=PENDING&statuses=PROCESSING
    @GetMapping("/shipping/city/{city}/statuses")
    public List<OrderDocument> findByShippingCityAndStatusIn(
            @PathVariable String city,
            @RequestParam List<String> statuses) {
        return orderService.findByShippingCityAndStatusIn(city, statuses);
    }

    // ==================== COMPLEX ====================

    // Orders containing a specific product in items array ($elemMatch)
    @GetMapping("/product/{productId}")
    public List<OrderDocument> findOrdersContainingProduct(@PathVariable String productId) {
        return orderService.findOrdersContainingProduct(productId);
    }

    // Status + minimum quantity in a single item line: ?status=DELIVERED&minQuantity=2
    @GetMapping("/status-qty")
    public List<OrderDocument> findByStatusWithMinItemQuantity(
            @RequestParam String status,
            @RequestParam(defaultValue = "2") int minQuantity) {
        return orderService.findByStatusWithMinItemQuantity(status, minQuantity);
    }

    // Stale orders: PENDING > pendingDays old OR PROCESSING > processingDays old
    @GetMapping("/stale")
    public List<OrderDocument> findStaleOrders(
            @RequestParam(defaultValue = "7") int pendingDays,
            @RequestParam(defaultValue = "14") int processingDays) {
        return orderService.findStaleOrders(pendingDays, processingDays);
    }

    // Active orders with at least one item above a unit price threshold
    @GetMapping("/expensive-items")
    public List<OrderDocument> findActiveOrdersWithExpensiveItems(@RequestParam Double minUnitPrice) {
        return orderService.findActiveOrdersWithExpensiveItems(minUnitPrice);
    }

    // Large active orders: high total + not cancelled/pending + multiple item lines
    @GetMapping("/large")
    public List<OrderDocument> findLargeActiveOrders(
            @RequestParam Double minTotal,
            @RequestParam(defaultValue = "2") int minItems) {
        return orderService.findLargeActiveOrders(minTotal, minItems);
    }

    // ==================== BASIC UPDATE ====================

    // PATCH: save() — carrega o pedido, altera status em memória e salva de volta
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDocument> updateStatus(
            @PathVariable String id,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }

    // ==================== MEDIUM UPDATE ====================

    // POST: $push + $inc — adiciona item e atualiza o total num único comando atômico
    @PostMapping("/{id}/items")
    public ResponseEntity<OrderDocument> addItem(
            @PathVariable String id,
            @RequestBody OrderItemDocument item) {
        return ResponseEntity.ok(orderService.addItem(id, item));
    }

    // DELETE: $pull + $inc — remove item do array e subtrai do total
    @DeleteMapping("/{id}/items/{productId}")
    public ResponseEntity<OrderDocument> removeItem(
            @PathVariable String id,
            @PathVariable String productId) {
        return ResponseEntity.ok(orderService.removeItem(id, productId));
    }

    // ==================== ADVANCED UPDATE ====================

    // PATCH bulk: updateMulti — cancela em massa pedidos PENDING mais antigos que N dias
    // Retorna a quantidade de documentos modificados
    @PatchMapping("/bulk-cancel")
    public ResponseEntity<Long> bulkCancelPendingOrders(
            @RequestParam(defaultValue = "30") int olderThanDays) {
        return ResponseEntity.ok(orderService.bulkCancelPendingOrders(olderThanDays));
    }

    // PATCH: AggregationUpdate (pipeline update) — recalcula totalAmount usando expressão MongoDB
    // Recalcula sum(unitPrice * quantity) de cada item diretamente no banco
    @PatchMapping("/{id}/recalculate")
    public ResponseEntity<OrderDocument> recalculateTotal(@PathVariable String id) {
        return ResponseEntity.ok(orderService.recalculateTotal(id));
    }
}
