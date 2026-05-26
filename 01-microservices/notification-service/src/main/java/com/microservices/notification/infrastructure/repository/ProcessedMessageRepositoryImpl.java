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
public class ProcessedMessageRepositoryImpl implements ProcessedMessageRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@inheritDoc}
     *
     * <p>{@code INSERT ... ON CONFLICT (message_id) DO NOTHING} eh nativo do Postgres
     * e nunca lanca exception em conflito de PK. Retorno do {@code executeUpdate()}:
     * <ul>
     *   <li>{@code 1} = inseriu (msg vista pela primeira vez)</li>
     *   <li>{@code 0} = nada inserido (msg ja existia — duplicata)</li>
     * </ul>
     * REQUIRES_NEW pra desacoplar do contexto de TX do listener: o dedupe nao
     * deve participar de rollback que o handler possa disparar depois.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsert(String messageId) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO processed_messages (message_id, processed_at)
                VALUES (:id, :now)
                ON CONFLICT (message_id) DO NOTHING
                """);
        query.setParameter("id", messageId);
        query.setParameter("now", Timestamp.valueOf(LocalDateTime.now()));
        return query.executeUpdate() == 1;
    }

    @Override
    @Transactional
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        Query query = entityManager.createNativeQuery(
                "DELETE FROM processed_messages WHERE processed_at < :cutoff");
        query.setParameter("cutoff", Timestamp.valueOf(cutoff));
        return query.executeUpdate();
    }
}
