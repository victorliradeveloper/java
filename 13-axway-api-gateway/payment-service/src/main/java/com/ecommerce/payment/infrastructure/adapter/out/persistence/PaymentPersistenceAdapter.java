package com.ecommerce.payment.infrastructure.adapter.out.persistence;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.port.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Payment save(Payment payment) {
        var entity = mapper.toJpaEntity(payment);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
