package com.microservices.notification.listener;

import com.microservices.notification.config.SqsConfig;
import com.microservices.notification.event.TodoEvent;
import com.microservices.notification.infrastructure.repository.ProcessedMessageRepository;
import com.microservices.notification.service.EmailService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodoEventListener {

    private final EmailService emailService;
    private final ProcessedMessageRepository processedMessageRepository;

    // spring-cloud-aws-sqs 3.x mapeia o SQS MessageId direto pro
    // MessageHeaders.ID padrao do Spring Messaging (como UUID). Eh o
    // mesmo valor em reentregas at-least-once da mesma mensagem.

    @SqsListener(SqsConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event,
                              @Header(MessageHeaders.ID) UUID messageId) {
        if (alreadyProcessed(messageId, event)) {
            return;
        }
        log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }

    @SqsListener(SqsConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event,
                              @Header(MessageHeaders.ID) UUID messageId) {
        if (alreadyProcessed(messageId, event)) {
            return;
        }
        log.info("[NOTIFICATION] Todo ATUALIZADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }

    @SqsListener(SqsConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event,
                              @Header(MessageHeaders.ID) UUID messageId) {
        if (alreadyProcessed(messageId, event)) {
            return;
        }
        log.info("[NOTIFICATION] Todo DELETADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }

    /**
     * Tenta gravar o messageId na tabela de dedupe. Se ja existia (segunda entrega
     * da mesma mensagem pelo SQS), retorna true e o evento eh descartado — a mensagem
     * eh ack'd normalmente, sem reprocessar.
     *
     * Insert acontece ANTES do envio do e-mail: se o e-mail falhar depois, a proxima
     * entrega sera descartada. Trade-off explicito: "perde raro" em vez de
     * "duplica raro" (ver .spec/idempotency.md §1.3).
     */
    private boolean alreadyProcessed(UUID messageId, TodoEvent event) {
        boolean inserted = processedMessageRepository.tryInsert(messageId.toString());
        if (!inserted) {
            log.info("[DEDUPE] mensagem duplicada descartada -> messageId={} | action={} | todoId={}",
                    messageId, event.action(), event.todoId());
            return true;
        }
        return false;
    }
}
