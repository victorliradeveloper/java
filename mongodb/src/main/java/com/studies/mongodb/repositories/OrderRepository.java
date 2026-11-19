package com.studies.mongodb.repositories;

import com.studies.mongodb.documents.OrderDocument;
import com.studies.mongodb.enums.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends MongoRepository<OrderDocument, String> {

    // ==================== SIMPLE ====================

    List<OrderDocument> findByStatus(OrderStatus status);

    // Spring Data MongoDB traverses the @DBRef automatically for derived queries
    List<OrderDocument> findByCustomerId(String customerId);

    List<OrderDocument> findByTotalAmountGreaterThan(Double amount);

    // ==================== MEDIUM ====================

    List<OrderDocument> findByCustomerIdAndStatus(String customerId, OrderStatus status);

    List<OrderDocument> findByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime start, LocalDateTime end);

    // Dot-notation on embedded document field (shippingAddress is not an array)
    @Query("{ 'shippingAddress.city': ?0 }")
    List<OrderDocument> findByShippingCity(String city);

    // Orders with total >= minTotal
    @Query("{ 'totalAmount': { $gte: ?0 } }")
    List<OrderDocument> findByMinimumTotal(Double minTotal);

    // Orders shipping to a city with one of the given statuses ($in on enum list)
    @Query("{ 'shippingAddress.city': ?0, 'status': { $in: ?1 } }")
    List<OrderDocument> findByShippingCityAndStatusIn(String city, List<String> statuses);

    // ==================== COMPLEX ====================

    // Orders containing a specific product inside the embedded items array
    @Query("{ 'items': { $elemMatch: { 'productId': ?0 } } }")
    List<OrderDocument> findOrdersContainingProduct(String productId);

    // Orders in a given status where at least one item has quantity >= minQuantity
    @Query("""
            { $and: [
                { 'status': ?0 },
                { 'items': { $elemMatch: { 'quantity': { $gte: ?1 } } } }
            ] }
            """)
    List<OrderDocument> findByStatusWithMinItemQuantity(String status, int minQuantity);

    // "Stale" orders: PENDING older than pendingCutoff OR PROCESSING older than processingCutoff
    @Query("""
            { $or: [
                { 'status': 'PENDING',    'createdAt': { $lt: ?0 } },
                { 'status': 'PROCESSING', 'createdAt': { $lt: ?1 } }
            ] }
            """)
    List<OrderDocument> findStaleOrders(LocalDateTime pendingCutoff, LocalDateTime processingCutoff);

    // Active (non-cancelled) orders with expensive items AND high total
    @Query("{ 'items': { $elemMatch: { 'unitPrice': { $gte: ?0 } } }, 'status': { $ne: 'CANCELLED' } }")
    List<OrderDocument> findActiveOrdersWithExpensiveItems(Double minUnitPrice);

    // Large orders: total >= minTotal, NOT cancelled/pending, with >= minItems item lines
    // Combines $nin, $expr, $size, and $gte in a single $and
    @Query("""
            { $and: [
                { 'totalAmount': { $gte: ?0 } },
                { 'status': { $nin: ['CANCELLED', 'PENDING'] } },
                { $expr: { $gte: [ { $size: '$items' }, ?1 ] } }
            ] }
            """)
    List<OrderDocument> findLargeActiveOrders(Double minTotal, int minItems);
}
