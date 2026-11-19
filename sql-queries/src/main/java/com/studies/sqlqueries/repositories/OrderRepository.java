package com.studies.sqlqueries.repositories;

import com.studies.sqlqueries.entities.Order;
import com.studies.sqlqueries.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ==================== SIMPLE ====================

    // SQL: SELECT * FROM orders WHERE status = ?
    List<Order> findByStatus(OrderStatus status);

    // Spring Data traverses the @ManyToOne association via the underscore separator.
    // SQL: SELECT * FROM orders WHERE customer_id = ?  (direct FK — no JOIN needed)
    // MongoDB: Spring Data MongoDB traverses the @DBRef automatically.
    List<Order> findByCustomer_Id(Long customerId);

    // SQL: SELECT * FROM orders WHERE total_amount > ?
    List<Order> findByTotalAmountGreaterThan(Double amount);

    // ==================== MEDIUM ====================

    // SQL: SELECT * FROM orders WHERE customer_id = ? AND status = ?
    List<Order> findByCustomer_IdAndStatus(Long customerId, OrderStatus status);

    // SQL: SELECT * FROM orders WHERE status = ? AND created_at BETWEEN ? AND ?
    List<Order> findByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime start, LocalDateTime end);

    // Dot-notation on an @Embedded field (shippingAddress is a column group in the orders table).
    // MongoDB: { 'shippingAddress.city': ?0 }
    // SQL: WHERE shipping_city = ? (no join — the column lives in the orders table itself)
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.city = :city")
    List<Order> findByShippingCity(@Param("city") String city);

    // SQL: SELECT * FROM orders WHERE total_amount >= ?
    @Query("SELECT o FROM Order o WHERE o.totalAmount >= :minTotal")
    List<Order> findByMinimumTotal(@Param("minTotal") Double minTotal);

    // IN clause with an enum list.
    // MongoDB: { 'shippingAddress.city': ?0, 'status': { $in: ?1 } }
    // SQL: WHERE shipping_city = ? AND status IN (?, ?, ...)
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.city = :city AND o.status IN :statuses")
    List<Order> findByShippingCityAndStatusIn(@Param("city") String city,
                                              @Param("statuses") List<OrderStatus> statuses);

    // ==================== COMPLEX ====================

    // JOIN to order_items to find orders containing a specific product.
    // MongoDB: { 'items': { $elemMatch: { 'productId': ?0 } } }
    // SQL: JOIN on the order_items collection table, filter by product_id column.
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i WHERE i.productId = :productId")
    List<Order> findOrdersContainingProduct(@Param("productId") Long productId);

    // $and in MongoDB: status = ? AND items contain at least one with quantity >= ?
    // SQL: JOIN on order_items; both conditions on the same row via alias 'i'.
    // The JOIN guarantees that the matched quantity belongs to an item in this order.
    @Query("""
            SELECT DISTINCT o FROM Order o JOIN o.items i
            WHERE o.status = :status AND i.quantity >= :minQuantity
            """)
    List<Order> findByStatusWithMinItemQuantity(@Param("status") OrderStatus status,
                                                @Param("minQuantity") int minQuantity);

    // "Stale" orders: OR between two status+date conditions.
    // MongoDB: { $or: [ { status:'PENDING', createdAt:{$lt:?0} }, { status:'PROCESSING', createdAt:{$lt:?1} } ] }
    // SQL: standard OR with two grouped conditions using AND inside each group.
    @Query(nativeQuery = true, value = """
            SELECT * FROM orders
            WHERE (status = 'PENDING'    AND created_at < :pendingCutoff)
               OR (status = 'PROCESSING' AND created_at < :processingCutoff)
            """)
    List<Order> findStaleOrders(@Param("pendingCutoff") LocalDateTime pendingCutoff,
                                @Param("processingCutoff") LocalDateTime processingCutoff);

    // Active orders with at least one expensive item.
    // MongoDB: { 'items': { $elemMatch: { 'unitPrice': { $gte: ?0 } } }, 'status': { $ne: 'CANCELLED' } }
    // SQL: JOIN on order_items + status exclusion.
    @Query("""
            SELECT DISTINCT o FROM Order o JOIN o.items i
            WHERE i.unitPrice >= :minUnitPrice AND o.status <> com.studies.sqlqueries.enums.OrderStatus.CANCELLED
            """)
    List<Order> findActiveOrdersWithExpensiveItems(@Param("minUnitPrice") Double minUnitPrice);

    // Combines NOT IN, and a correlated subquery for item count.
    // MongoDB: { $and: [ { totalAmount:{$gte:?0} }, { status:{$nin:['CANCELLED','PENDING']} }, { $expr:{$gte:[{$size:'$items'},?1]} } ] }
    // SQL:
    //   - NOT IN ('CANCELLED','PENDING') → equivalent to $nin
    //   - subquery COUNT(*) >= :minItems  → equivalent to $expr + $size
    @Query(nativeQuery = true, value = """
            SELECT o.* FROM orders o
            WHERE o.total_amount >= :minTotal
              AND o.status NOT IN ('CANCELLED', 'PENDING')
              AND (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) >= :minItems
            """)
    List<Order> findLargeActiveOrders(@Param("minTotal") Double minTotal,
                                      @Param("minItems") int minItems);
}
