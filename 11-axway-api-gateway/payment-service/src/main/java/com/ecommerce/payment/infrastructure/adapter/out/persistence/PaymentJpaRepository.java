package com.ecommerce.payment.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByOrderId(Long orderId);
}
