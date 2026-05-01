package com.example.userservice.infrastructure.persistence.adapter;

import com.example.userservice.domain.model.Order;
import com.example.userservice.domain.port.out.OrderRepositoryPort;
import com.example.userservice.infrastructure.persistence.repository.OrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository repository;

    @Override
    public Order save(Order order) {
        return repository.save(order);
    }
}
