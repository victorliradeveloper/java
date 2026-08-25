package com.example.userservice.interfaces.mapper;

import com.example.userservice.domain.model.Order;
import com.example.userservice.domain.model.User;
import com.example.userservice.interfaces.dto.request.OrderRequestDTO;
import com.example.userservice.interfaces.dto.response.OrderResponseDTO;

public class OrderMapper {

    private OrderMapper() {}

    public static Order toEntity(OrderRequestDTO dto, User user) {
        return Order.builder()
                .description(dto.description())
                .amount(dto.amount())
                .user(user)
                .build();
    }

    public static OrderResponseDTO toResponse(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getDescription(),
                order.getAmount(),
                order.getCreatedAt()
        );
    }
}
