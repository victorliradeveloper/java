package com.example.userservice.domain.port.out;

import com.example.userservice.domain.model.Order;

public interface OrderRepositoryPort {

    Order save(Order order);
}
