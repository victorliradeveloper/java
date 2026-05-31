package com.microservices.todo.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.todo.exception.IdempotencyKeyConflictException;
import com.microservices.todo.exception.IdempotencyKeyConflictException.Reason;
import com.microservices.todo.infrastructure.entity.IdempotencyKey;
import com.microservices.todo.infrastructure.repository.IdempotencyKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Wrapper generico de idempotencia, reutilizavel em qualquer endpoint.
 *
 * <h3>Semantica</h3>
 * Padrao Stripe. Cliente envia <code>Idempotency-Key</code> no header; o servico:
 * <ol>
 *   <li>Tenta gravar um claim atomico na collection <code>idempotency_keys</code>.</li>
 *   <li>Se inserir (claim novo): executa a operacao, grava o response no doc, retorna.</li>
 *   <li>Se conflito (claim ja existe): valida hash do payload e retorna response cacheada
 *       ou lanca {@link IdempotencyKeyConflictException} com {@link Reason} apropriada.</li>
 * </ol>
 *
 * <h3>Race conditions</h3>
 * O claim eh atomico via unique index em <code>_id</code> + {@code insert()}.
 * Duas chamadas simultaneas com a mesma key: uma vence o insert, a outra cai
 * em {@link DuplicateKeyException} e e' tratada no caminho de "ja existe".
 *
 * <h3>Falha da operacao vs falha do cache</h3>
 * Sao tratadas de forma diferente:
 * <ul>
 *   <li><b>Operacao lanca</b>: claim eh deletado, excecao propaga. Cliente vê o erro
 *       real e pode retentar com a mesma key.</li>
 *   <li><b>Cache (save do claim com response) lanca</b>: erro eh logado mas a resposta
 *       eh retornada. Operacao ja sucedeu — cliente precisa receber 201 com o recurso
 *       criado. Trade-off: retry dentro da janela TTL pode receber 409 IN_PROGRESS,
 *       mas isso eh muito melhor que perder o 201 e o cliente criar um recurso duplicado.</li>
 * </ul>
 *
 * <h3>Limpeza</h3>
 * TTL index em <code>expires_at</code> (V004) — Mongo apaga docs expirados em
 * background. Sem job manual.
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final String MDC_KEY = "idempotencyKey";
    private static final int ASCII_PRINTABLE_MIN = 0x20;
    private static final int ASCII_PRINTABLE_MAX = 0x7E;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration keyTtl;
    private final int maxKeyLength;

    public IdempotencyService(IdempotencyKeyRepository repository,
                              ObjectMapper objectMapper,
                              @Value("${idempotency.key-ttl:24h}") Duration keyTtl,
                              @Value("${idempotency.max-key-length:255}") int maxKeyLength) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.keyTtl = keyTtl;
        this.maxKeyLength = maxKeyLength;
    }

    /**
     * Executa {@code operation} com semantica idempotente. Se {@code idempotencyKey}
     * for {@code null}, executa diretamente (sem idempotencia).
     *
     * @param idempotencyKey       chave enviada pelo cliente; {@code null} = sem idempotencia
     * @param operationFingerprint identificador da operacao (ex.: {@code "POST /todos"});
     *                             previne reuso de key entre endpoints distintos
     * @param requestPayload       corpo da request, usado pra calcular hash de integridade
     * @param responseType         tipo do retorno (necessario pra desserializar response cacheada)
     * @param operation            operacao de negocio a ser executada
     * @return resultado da operacao (novo) ou response cacheada
     * @throws IdempotencyKeyConflictException 409 em hash mismatch, request em andamento, ou key invalida
     */
    public <T> T executeIdempotent(String idempotencyKey,
                                   String operationFingerprint,
                                   Object requestPayload,
                                   Class<T> responseType,
                                   Supplier<T> operation) {
        if (idempotencyKey == null) {
            return operation.get();
        }
        validateKey(idempotencyKey);

        String requestHash = hash(operationFingerprint, requestPayload);
        MDC.put(MDC_KEY, idempotencyKey);
        try {
            return runIdempotent(idempotencyKey, requestHash, responseType, operation);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private <T> T runIdempotent(String idempotencyKey,
                                String requestHash,
                                Class<T> responseType,
                                Supplier<T> operation) {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyKey claim = IdempotencyKey.builder()
                .key(idempotencyKey)
                .requestHash(requestHash)
                .createdAt(now)
                .expiresAt(now.plus(keyTtl))
                .build();

        try {
            repository.insert(claim);
        } catch (DuplicateKeyException e) {
            return replayExisting(idempotencyKey, requestHash, responseType);
        }

        T response;
        try {
            response = operation.get();
        } catch (RuntimeException ex) {
            releaseClaimOnFailure(idempotencyKey, ex);
            throw ex;
        }

        cacheResponseBestEffort(claim, response);
        return response;
    }

    private <T> T replayExisting(String idempotencyKey,
                                 String requestHash,
                                 Class<T> responseType) {
        IdempotencyKey existing = repository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency-Key '" + idempotencyKey + "' sumiu apos DuplicateKeyException — possivel TTL race"));

        if (!existing.getRequestHash().equals(requestHash)) {
            log.warn("[IDEMPOTENCY] hash mismatch key={}", idempotencyKey);
            throw new IdempotencyKeyConflictException(idempotencyKey, Reason.PAYLOAD_MISMATCH);
        }
        if (existing.getResponseBody() == null) {
            log.warn("[IDEMPOTENCY] requisicao concorrente em processamento key={}", idempotencyKey);
            throw new IdempotencyKeyConflictException(idempotencyKey, Reason.IN_PROGRESS);
        }
        log.info("[IDEMPOTENCY] retornando response cacheada key={}", idempotencyKey);
        return deserialize(existing.getResponseBody(), responseType);
    }

    /**
     * Grava o response no claim. Em falha, loga ERROR mas NAO propaga — operacao
     * de negocio ja sucedeu, cliente precisa receber 201 com o recurso criado.
     * Consequencia aceita: retry dentro da janela TTL pode bater no IN_PROGRESS
     * (ate o doc expirar e ser removido pelo TTL).
     */
    private <T> void cacheResponseBestEffort(IdempotencyKey claim, T response) {
        try {
            claim.markCompleted(201, serialize(response));
            repository.save(claim);
            log.info("[IDEMPOTENCY] claim resolvido com sucesso key={}", claim.getKey());
        } catch (RuntimeException ex) {
            log.error("[IDEMPOTENCY] operation OK mas falha ao cachear response key={} — retry com mesma key pode retornar 409 IN_PROGRESS ate TTL expirar",
                    claim.getKey(), ex);
        }
    }

    private void releaseClaimOnFailure(String idempotencyKey, RuntimeException ex) {
        try {
            repository.deleteById(idempotencyKey);
            log.warn("[IDEMPOTENCY] operation falhou, claim liberado key={} err={}",
                    idempotencyKey, ex.getClass().getSimpleName());
        } catch (RuntimeException cleanupEx) {
            // Cleanup falhou — claim vai persistir ate o TTL expirar. TTL eh a defesa final.
            log.error("[IDEMPOTENCY] falha ao liberar claim key={} — sera limpo pelo TTL",
                    idempotencyKey, cleanupEx);
        }
    }

    private void validateKey(String key) {
        if (key.isEmpty() || key.length() > maxKeyLength) {
            throw new IdempotencyKeyConflictException(key, Reason.INVALID_KEY);
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < ASCII_PRINTABLE_MIN || c > ASCII_PRINTABLE_MAX) {
                throw new IdempotencyKeyConflictException(key, Reason.INVALID_KEY);
            }
        }
    }

    private String hash(String fingerprint, Object payload) {
        try {
            String content = fingerprint + "\n" + objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao computar hash da request", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar response pra cache", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao desserializar response cacheada", e);
        }
    }
}
