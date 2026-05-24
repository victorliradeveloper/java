package com.microservices.todo.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Chave de idempotencia enviada pelo cliente no header Idempotency-Key.
 *
 * Fluxo: cliente envia UUID no header. Server insere doc com unique index
 * em _id. Se inserir, processa a requisicao e grava a resposta. Se cair
 * DuplicateKeyException, ja existe — retorna a resposta cacheada (se hash
 * bate) ou 409 (se hash difere).
 *
 * TTL via indice em expires_at (criado pela V004): docs expirados sao
 * removidos automaticamente pelo Mongo. Padrao Stripe: 24h.
 */
@Document(collection = "idempotency_keys")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    private String key;

    @Field("request_hash")
    private String requestHash;

    // null enquanto a requisicao ainda esta em processamento (claim feito,
    // create ainda nao retornou). Setado depois pelo markCompleted.
    @Field("response_status")
    private Integer responseStatus;

    @Field("response_body")
    private String responseBody;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("expires_at")
    private LocalDateTime expiresAt;

    public void markCompleted(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }
}
