package com.ecommerce.order.infrastructure.adapter.in.web;

import com.ecommerce.order.domain.port.in.CreateOrderUseCase;
import com.ecommerce.order.domain.port.in.FindOrdersUseCase;
import com.ecommerce.order.domain.port.in.command.CreateOrderCommand;
import com.ecommerce.order.domain.port.in.command.OrderItemCommand;
import com.ecommerce.order.infrastructure.adapter.in.web.dto.OrderRequest;
import com.ecommerce.order.infrastructure.adapter.in.web.dto.OrderResponse;
import com.ecommerce.order.infrastructure.config.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final FindOrdersUseCase findOrdersUseCase;
    private final CreateOrderUseCase createOrderUseCase;
    private final JwtService jwtService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<List<OrderResponse>> myOrders(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        var orders = findOrdersUseCase.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(OrderResponse.from(findOrdersUseCase.findById(id, userId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);

        var items = request.items().stream()
                .map(i -> new OrderItemCommand(i.productId(), i.productName(), i.quantity(), i.price()))
                .toList();

        var command = new CreateOrderCommand(userId, items);
        var created = createOrderUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(created));
    }

    private Long extractUserId(String authHeader) {
        return jwtService.extractClaim(authHeader.substring(7), claims -> claims.get("userId", Long.class));
    }
}
