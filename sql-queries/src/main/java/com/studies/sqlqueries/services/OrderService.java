package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Order;
import com.studies.sqlqueries.entities.OrderItem;
import com.studies.sqlqueries.enums.OrderStatus;
import com.studies.sqlqueries.repositories.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // ==================== BASIC UPDATE ====================

    // @Modifying @Query → UPDATE orders SET status=?, updated_at=? WHERE id=?
    // MongoDB: $set via MongoTemplate ou save()
    // Mais eficiente que save() pois não carrega a entidade inteira.
    @Transactional
    public Order updateStatus(Long id, OrderStatus newStatus) {
        findByIdOrThrow(id);
        orderRepository.updateStatus(id, newStatus, LocalDateTime.now());
        return findByIdOrThrow(id);
    }

    // ==================== MEDIUM UPDATE ====================

    // $push + $inc equivalente: carrega o pedido, adiciona o item ao List,
    // recalcula o total na aplicação e salva.
    // JPA gera: INSERT INTO order_items + UPDATE orders SET total_amount=?
    @Transactional
    public Order addItem(Long id, OrderItem item) {
        var order = findByIdOrThrow(id);
        order.getItems().add(item);
        order.setTotalAmount(order.getTotalAmount() + item.getUnitPrice() * item.getQuantity());
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // $pull + $inc equivalente: carrega, remove o item e subtrai do total.
    // JPA recria todas as linhas de order_items para este pedido.
    @Transactional
    public Order removeItem(Long id, Long productId) {
        var order = findByIdOrThrow(id);
        var item = order.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in order"));

        order.getItems().remove(item);
        order.setTotalAmount(order.getTotalAmount() - item.getUnitPrice() * item.getQuantity());
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti equivalente: @Modifying @Query → UPDATE N pedidos sem loop na aplicação.
    // SQL: UPDATE orders SET status='CANCELLED', updated_at=? WHERE status='PENDING' AND created_at < ?
    @Transactional
    public int bulkCancelPendingOrders(int olderThanDays) {
        var cutoff = LocalDateTime.now().minusDays(olderThanDays);
        return orderRepository.bulkUpdateStatus(
                OrderStatus.PENDING, OrderStatus.CANCELLED, cutoff, LocalDateTime.now());
    }

    // AggregationUpdate (pipeline update) equivalente: @Modifying nativeQuery com subquery no SET.
    // SQL: UPDATE orders SET total_amount = (SELECT SUM(unit_price * quantity) FROM order_items WHERE order_id = ?)
    // Recalcula o total diretamente no banco sem carregar o pedido — equivalente ao MongoDB pipeline update.
    @Transactional
    public Order recalculateTotal(Long id) {
        findByIdOrThrow(id);
        orderRepository.recalculateTotal(id, LocalDateTime.now());
        return findByIdOrThrow(id);
    }

    private Order findByIdOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
