package com.ecommerce.notification.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {
    List<NotificationJpaEntity> findByUserId(Long userId);
}
