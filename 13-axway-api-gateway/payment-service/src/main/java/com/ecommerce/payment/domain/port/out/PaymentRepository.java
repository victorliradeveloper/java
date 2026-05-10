package com.ecommerce.payment.domain.port.out;

import com.ecommerce.payment.domain.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAll();

    Payment save(Payment payment);
}
