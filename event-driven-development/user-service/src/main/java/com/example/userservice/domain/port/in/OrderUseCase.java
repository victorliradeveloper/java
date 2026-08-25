package com.example.userservice.domain.port.in;

import com.example.userservice.domain.model.User;
import com.example.userservice.interfaces.dto.request.OrderRequestDTO;
import com.example.userservice.interfaces.dto.response.OrderResponseDTO;

public interface OrderUseCase {
    OrderResponseDTO create(OrderRequestDTO request, User user);
}
