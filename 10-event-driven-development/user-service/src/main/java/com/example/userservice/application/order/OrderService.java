package com.example.userservice.application.order;

import com.example.userservice.domain.model.Order;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.port.out.OrderRepositoryPort;
import com.example.userservice.infrastructure.messaging.UserEventPublisher;
import com.example.userservice.interfaces.dto.request.OrderRequestDTO;
import com.example.userservice.interfaces.dto.response.OrderResponseDTO;
import com.example.userservice.interfaces.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepositoryPort orderRepository;
    private final UserEventPublisher eventPublisher;

    public OrderResponseDTO create(OrderRequestDTO request, User user) {
        Order order = OrderMapper.toEntity(request, user);
        order = orderRepository.save(order);
        eventPublisher.publishOrderCreated(order, user);
        return OrderMapper.toResponse(order);
    }
}
