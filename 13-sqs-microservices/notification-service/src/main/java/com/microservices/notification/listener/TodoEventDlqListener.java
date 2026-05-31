package com.microservices.notification.listener;

import com.microservices.notification.config.SqsConfig;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listener das DLQs. Loga WARN com o body bruto de cada mensagem que excedeu
 * maxReceiveCount na fila principal e foi parar aqui.
 *
 * Recebe String (nao TodoEvent) porque a causa mais comum de chegar na DLQ
 * eh justamente payload malformado — desserializar de novo daria o mesmo erro
 * em loop. String aceita qualquer body.
 *
 * Em producao real este listener seria substituido por um alarme em
 * ApproximateNumberOfMessagesVisible da DLQ; a mensagem ficaria retida pra
 * inspecao manual + redrive. Aqui ele acka (auto-delete no return) pra ter
 * sinal imediato em dev sem poluir log a cada visibility timeout.
 */
@Slf4j
@Component
public class TodoEventDlqListener {

    @SqsListener(SqsConfig.QUEUE_CREATED_DLQ)
    public void onCreatedDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
        log.warn("[DLQ] {} -> messageId={} body={}", SqsConfig.QUEUE_CREATED_DLQ, messageId, body);
    }

    @SqsListener(SqsConfig.QUEUE_UPDATED_DLQ)
    public void onUpdatedDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
        log.warn("[DLQ] {} -> messageId={} body={}", SqsConfig.QUEUE_UPDATED_DLQ, messageId, body);
    }

    @SqsListener(SqsConfig.QUEUE_DELETED_DLQ)
    public void onDeletedDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
        log.warn("[DLQ] {} -> messageId={} body={}", SqsConfig.QUEUE_DELETED_DLQ, messageId, body);
    }
}
