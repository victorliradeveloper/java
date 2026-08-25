package com.example.userservice.infrastructure.persistence.repository;

import com.example.userservice.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {}
