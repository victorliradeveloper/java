package com.microservices.audit.infrastructure.repository;

import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Repository
public class TodoAuditLogRepositoryImpl implements TodoAuditLogRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertIfAbsent(TodoAuditLog log) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO todo_audit_log
                    (message_id, aggregate_id, title, event_type, occurred_at, recorded_at)
                VALUES (:messageId, :aggregateId, :title, :eventType, :occurredAt, :recordedAt)
                ON CONFLICT (message_id) DO NOTHING
                """);
        query.setParameter("messageId", log.getMessageId());
        query.setParameter("aggregateId", log.getAggregateId());
        query.setParameter("title", log.getTitle());
        query.setParameter("eventType", log.getEventType());
        query.setParameter("occurredAt", Timestamp.valueOf(log.getOccurredAt()));
        query.setParameter("recordedAt", Timestamp.valueOf(log.getRecordedAt()));
        return query.executeUpdate() == 1;
    }
}
