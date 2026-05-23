package com.microservices.audit.listener;

import com.microservices.audit.config.SqsConfig;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listener da DLQ do audit-service. Loga WARN com body bruto de cada mensagem
 * que excedeu maxReceiveCount na fila principal.
 *
 * Recebe String (nao TodoEvent) porque payloads malformados sao a causa mais
 * comum de chegar aqui — desserializar de novo daria o mesmo erro em loop.
 *
 * Ver .spec/03-patterns/dlq.md para o trade-off de ackar vs reter.
 */
@Slf4j
@Component
public class TodoEventAuditDlqListener {

    @SqsListener(SqsConfig.QUEUE_AUDIT_DLQ)
    public void onAuditDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
        log.warn("[DLQ] {} -> messageId={} body={}", SqsConfig.QUEUE_AUDIT_DLQ, messageId, body);
    }
}
