package com.ecommerce.payment.domain.port.in;

import com.ecommerce.payment.domain.model.Payment;

import java.util.List;
import java.util.Optional;

public interface FindPaymentsUseCase {
    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAll();
}
