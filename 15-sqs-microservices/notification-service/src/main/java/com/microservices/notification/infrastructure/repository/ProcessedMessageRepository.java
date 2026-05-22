package com.microservices.notification.infrastructure.repository;

import com.microservices.notification.infrastructure.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /**
     * Insere o messageId atomicamente. Retorna 1 se inseriu (primeira vez),
     * 0 se já existia (duplicata). Usa ON CONFLICT do Postgres para evitar
     * race condition entre múltiplas instâncias do consumer.
     */
    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO processed_messages (message_id, processed_at) " +
                    "VALUES (:messageId, NOW()) " +
                    "ON CONFLICT (message_id) DO NOTHING",
            nativeQuery = true
    )
    int tryInsert(@Param("messageId") String messageId);
}
