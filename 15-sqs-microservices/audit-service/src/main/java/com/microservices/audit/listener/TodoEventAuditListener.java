package com.microservices.audit.listener;

import com.microservices.audit.config.SqsConfig;
import com.microservices.audit.event.TodoEvent;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import com.microservices.audit.infrastructure.repository.TodoAuditLogRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodoEventAuditListener {

    private final TodoAuditLogRepository repository;

    /**
     * Consome todos os eventos do topic (todo-audit-queue inscrito sem filtro).
     *
     * Dedupe via _id = MessageId do SQS: tentativa de insert duplicado lanca
     * DuplicateKeyException, que tratamos como "ja processei, ack normal".
     * Mais barato e mais correto que tabela processed_messages separada —
     * o proprio insert eh a verificacao atomica.
     */
    @SqsListener(SqsConfig.QUEUE_AUDIT)
    public void onTodoEvent(TodoEvent event,
                            @Header(MessageHeaders.ID) UUID messageId) {
        TodoAuditLog log = TodoAuditLog.builder()
                .id(messageId.toString())
                .aggregateId(event.todoId())
                .title(event.title())
                .eventType(event.action())
                .occurredAt(event.occurredAt())
                .recordedAt(LocalDateTime.now())
                .build();

        try {
            repository.insert(log);
            TodoEventAuditListener.log.info(
                    "[AUDIT] registrado messageId={} todoId={} action={}",
                    messageId, event.todoId(), event.action());
        } catch (DuplicateKeyException e) {
            TodoEventAuditListener.log.info(
                    "[AUDIT][DEDUPE] mensagem duplicada descartada messageId={} todoId={} action={}",
                    messageId, event.todoId(), event.action());
        }
    }
}
