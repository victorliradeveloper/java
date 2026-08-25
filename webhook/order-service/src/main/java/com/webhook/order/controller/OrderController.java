package com.webhook.order.controller;

import com.webhook.order.dto.request.CreateOrderRequest;
import com.webhook.order.dto.response.OrderResponse;
import com.webhook.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        OrderResponse response = OrderResponse.from(orderService.create(request));
        URI location = uriBuilder.path("/orders/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable UUID id) {
        return OrderResponse.from(orderService.findById(id));
    }
}
