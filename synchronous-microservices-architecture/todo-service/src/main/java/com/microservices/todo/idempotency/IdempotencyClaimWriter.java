package com.microservices.todo.idempotency;

import com.microservices.todo.infrastructure.entity.IdempotencyKey;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wrapper transacional pra INSERT puro do claim de idempotencia.
 *
 * <p>Usa {@link EntityManager#persist} (nao {@code save()}) deliberadamente:
 * {@code persist()} sempre tenta INSERT, enquanto {@code save()} faria UPSERT
 * em entidades com @Id atribuido. Precisamos da violacao de PK na concorrencia.
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyClaimWriter {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(IdempotencyKey claim) {
        entityManager.persist(claim);
        entityManager.flush();
    }
}
