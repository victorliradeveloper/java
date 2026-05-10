package com.ecommerce.user.infrastructure.adapter.out.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntryJpaEntity, Long> {

    @Query("SELECT o FROM OutboxEntryJpaEntity o WHERE o.status IN ('PENDING', 'FAILED') AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC")
    List<OutboxEntryJpaEntity> findUnpublished(int maxRetries, Pageable pageable);
}
