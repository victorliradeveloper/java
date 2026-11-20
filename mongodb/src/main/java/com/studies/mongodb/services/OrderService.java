package com.studies.mongodb.services;

import com.studies.mongodb.documents.OrderDocument;
import com.studies.mongodb.documents.embedded.OrderItemDocument;
import com.studies.mongodb.enums.OrderStatus;
import com.studies.mongodb.repositories.OrderRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;

    public OrderService(OrderRepository orderRepository, MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
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

    // ==================== BASIC UPDATE ====================

    // save() — carrega o documento, altera o status em memória e salva de volta
    // Útil quando você precisa fazer validações antes de salvar
    public OrderDocument updateStatus(String id, OrderStatus newStatus) {
        var order = findByIdOrThrow(id);
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // ==================== MEDIUM UPDATE ====================

    // $push + $inc em uma única operação — adiciona item e atualiza o total atomicamente
    // Combinar múltiplos operadores no mesmo Update garante atomicidade
    public OrderDocument addItem(String id, OrderItemDocument item) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update()
                .push("items", item)
                .inc("totalAmount", item.unitPrice() * item.quantity())
                .set("updatedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, OrderDocument.class);
        return findByIdOrThrow(id);
    }

    // $pull + $inc — remove item do array e subtrai seu valor do total
    // Lê o pedido primeiro apenas para calcular o valor a subtrair
    public OrderDocument removeItem(String id, String productId) {
        var order = findByIdOrThrow(id);
        var item = order.getItems().stream()
                .filter(i -> i.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in order"));

        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update()
                .pull("items", new Document("productId", productId))
                .inc("totalAmount", -(item.unitPrice() * item.quantity()))
                .set("updatedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, OrderDocument.class);
        return findByIdOrThrow(id);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti — cancela em lote todos os pedidos PENDING mais antigos que N dias
    // Uma única query no banco cancela N pedidos sem loop na aplicação
    public long bulkCancelPendingOrders(int olderThanDays) {
        var cutoff = LocalDateTime.now().minusDays(olderThanDays);
        var query = Query.query(
                Criteria.where("status").is(OrderStatus.PENDING)
                        .and("createdAt").lt(cutoff)
        );
        var update = new Update()
                .set("status", OrderStatus.CANCELLED)
                .set("updatedAt", LocalDateTime.now());
        return mongoTemplate.updateMulti(query, update, OrderDocument.class).getModifiedCount();
    }

    // AggregationUpdate (pipeline update) — MongoDB 4.2+
    // Permite usar expressões de agregação dentro do update.
    // Aqui recalcula totalAmount diretamente do array items sem carregar o documento.
    // Equivalente MongoDB: { $set: { totalAmount: { $sum: { $map: { input: "$items",
    //   as: "item", in: { $multiply: ["$$item.unitPrice", "$$item.quantity"] } } } } } }
    public OrderDocument recalculateTotal(String id) {
        var query = Query.query(Criteria.where("_id").is(id));

        AggregationExpression totalExpr = ctx -> new Document("$sum",
                new Document("$map", new Document()
                        .append("input", "$items")
                        .append("as", "item")
                        .append("in", new Document("$multiply",
                                List.of("$$item.unitPrice", "$$item.quantity")))
                )
        );

        var update = AggregationUpdate.update()
                .set("totalAmount").toValueOf(totalExpr)
                .set("updatedAt").toValue(LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, OrderDocument.class);
        return findByIdOrThrow(id);
    }

    private OrderDocument findByIdOrThrow(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
