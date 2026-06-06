package com.microservices.audit.listener;

import com.microservices.audit.config.RabbitMQConfig;
import com.microservices.audit.event.TodoEvent;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import com.microservices.audit.infrastructure.repository.TodoAuditLogRepository;
import com.microservices.audit.mapper.TodoAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consome TODOS os eventos do dominio Todo (uma fila so, bindada com {@code todo.#})
 * e grava na tabela {@code todo_audit_log}, que eh append-only.
 *
 * <p><b>Dedupe via PK natural</b>: o {@code messageId} (id AMQP setado pelo
 * {@code OutboxPublisher}) eh a chave primaria. {@code insertIfAbsent} usa
 * {@code ON CONFLICT DO NOTHING} — se a msg ja foi auditada antes, o INSERT
 * eh ignorado. Mais simples e correto que tabela {@code processed_messages}
 * separada: a propria insercao da auditoria EH a verificacao atomica de "ja vi".
 *
 * <p><b>Por que sem messageId nao processa?</b> Sem ele nao da pra deduplicar.
 * Em runtime real isso nunca acontece (o OutboxPublisher sempre seta), entao
 * preferimos descartar a logar barulho do que arriscar duplicar registros.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoAuditListener {

    private final TodoAuditLogRepository repository;
    private final TodoAuditLogMapper mapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_AUDIT)
    public void onTodoEvent(TodoEvent event,
                            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (messageId == null || messageId.isBlank()) {
            log.warn("[AUDIT] msg sem messageId — descartada action={} todoId={}",
                    event.action(), event.todoId());
            return;
        }

        TodoAuditLog auditLog = mapper.toAuditLog(event, messageId);

        boolean inserted = repository.insertIfAbsent(auditLog);
        if (inserted) {
            log.info("[AUDIT] registrado messageId={} todoId={} action={}",
                    messageId, event.todoId(), event.action());
        } else {
            log.info("[AUDIT][DEDUPE] mensagem duplicada descartada messageId={} todoId={} action={}",
                    messageId, event.todoId(), event.action());
        }
    }
}
