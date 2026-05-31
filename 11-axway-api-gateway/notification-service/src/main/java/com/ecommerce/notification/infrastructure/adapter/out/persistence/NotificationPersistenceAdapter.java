package com.ecommerce.notification.infrastructure.adapter.out.persistence;

import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;
    private final NotificationPersistenceMapper mapper;

    @Override
    public List<Notification> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Notification save(Notification notification) {
        var entity = mapper.toJpaEntity(notification);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
