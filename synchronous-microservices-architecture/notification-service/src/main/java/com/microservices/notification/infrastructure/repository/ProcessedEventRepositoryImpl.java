package com.microservices.notification.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class ProcessedEventRepositoryImpl implements ProcessedEventRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * REQUIRES_NEW pra desacoplar do contexto de TX do controller: o dedupe nao
     * deve participar de rollback que o handler possa disparar depois (ex.: SMTP
     * falha apos o INSERT ter sucedido).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsert(String eventId) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO processed_events (event_id, processed_at)
                VALUES (:id, :now)
                ON CONFLICT (event_id) DO NOTHING
                """);
        query.setParameter("id", eventId);
        query.setParameter("now", Timestamp.valueOf(LocalDateTime.now()));
        return query.executeUpdate() == 1;
    }

    @Override
    @Transactional
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        Query query = entityManager.createNativeQuery(
                "DELETE FROM processed_events WHERE processed_at < :cutoff");
        query.setParameter("cutoff", Timestamp.valueOf(cutoff));
        return query.executeUpdate();
    }
}
