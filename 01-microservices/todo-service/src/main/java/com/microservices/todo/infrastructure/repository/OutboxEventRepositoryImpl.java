package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementacao do claim atomico de outbox via {@code SELECT FOR UPDATE SKIP LOCKED}.
 *
 * <p>O padrao recomendado em Postgres pra workers concorrentes: o {@code SKIP LOCKED}
 * faz com que workers ignorem linhas ja travadas por outros, sem bloquear. Cada
 * linha eh entregue a apenas um worker por vez.
 *
 * <p>Implementado em duas queries dentro de uma TX:
 * <ol>
 *   <li>{@code SELECT id ... FOR UPDATE SKIP LOCKED LIMIT 1} — adquire o lock</li>
 *   <li>{@code UPDATE ... WHERE id = ?} — marca o lease no doc</li>
 *   <li>{@code SELECT ... WHERE id = ?} — retorna o estado atualizado</li>
 * </ol>
 * Alternativa em uma so query ({@code UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED ...) RETURNING *})
 * eh mais elegante mas o suporte do Hibernate ao {@code RETURNING} eh inconsistente
 * entre versoes — preferimos a opcao explicita.
 */
public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<OutboxEvent> claimNext(String nodeId, Duration leaseDuration) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.plus(leaseDuration);

        Query selectQuery = entityManager.createNativeQuery("""
                SELECT id FROM outbox_events
                 WHERE published_at IS NULL
                   AND (lease_expires_at IS NULL OR lease_expires_at < :now)
                   AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                 ORDER BY created_at ASC
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
                """);
        selectQuery.setParameter("now", Timestamp.valueOf(now));

        @SuppressWarnings("unchecked")
        List<Object> ids = selectQuery.getResultList();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        String claimedId = ids.get(0).toString();

        Query updateQuery = entityManager.createNativeQuery("""
                UPDATE outbox_events
                   SET processing_node  = :nodeId,
                       lease_expires_at = :leaseExpiry
                 WHERE id = :id
                """);
        updateQuery.setParameter("nodeId", nodeId);
        updateQuery.setParameter("leaseExpiry", Timestamp.valueOf(leaseExpiry));
        updateQuery.setParameter("id", claimedId);
        updateQuery.executeUpdate();

        // O entity manager pode ter cache stale do claim anterior; force reload.
        entityManager.flush();
        OutboxEvent fresh = entityManager.find(OutboxEvent.class, claimedId);
        if (fresh != null) {
            entityManager.refresh(fresh);
        }
        return Optional.ofNullable(fresh);
    }
}
