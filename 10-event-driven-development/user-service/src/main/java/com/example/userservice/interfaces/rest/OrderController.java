package com.example.userservice.interfaces.rest;

import com.example.userservice.application.order.OrderService;
import com.example.userservice.domain.model.User;
import com.example.userservice.interfaces.dto.request.OrderRequestDTO;
import com.example.userservice.interfaces.dto.response.OrderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Criação de pedidos")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar pedido (requer autenticação)")
    @SecurityRequirement(name = "bearerAuth")
    public OrderResponseDTO create(
            @Valid @RequestBody OrderRequestDTO request,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        return orderService.create(request, user);
    }
}
