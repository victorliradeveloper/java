package com.microservices.notification.listener;

import com.microservices.notification.config.RabbitMQConfig;
import com.microservices.notification.event.TodoEvent;
import com.microservices.notification.infrastructure.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consome eventos do dominio Todo com:
 * <ul>
 *   <li><b>Dedupe</b> via {@code processed_messages} (idempotencia at-least-once).</li>
 *   <li><b>Retry</b> em memória configurado em {@code spring.rabbitmq.listener.simple.retry}.</li>
 *   <li><b>DLQ routing</b>: depois de esgotadas as tentativas, a msg eh rejeitada sem
 *       requeue e o RabbitMQ a roteia pela DLX configurada em {@link RabbitMQConfig}.</li>
 * </ul>
 *
 * <h3>Por que dedupar DEPOIS do processamento?</h3>
 * Se gravarmos no {@code processed_messages} ANTES de fazer o trabalho, uma falha
 * no trabalho deixaria a mensagem marcada como processada mas sem efeito visivel.
 * Resultado: "perde raro". Marcando DEPOIS, no pior caso reentregamos e o trabalho
 * roda 2x — "duplica raro". Duplicar eh quase sempre menos pior que perder.
 *
 * <h3>Como reconhecemos a mesma msg?</h3>
 * RabbitMQ nao gera um messageId universal automatico. Quem garante esse id eh o
 * publisher: o {@code OutboxPublisher} setou o {@code outboxEvent.getId()} como
 * messageId no momento do publish (header AMQP {@code message-id}). Pra mensagens
 * sem esse header, fallback no {@code spring_returned_message_correlation} ou no
 * proprio body — mas com Outbox, sempre teremos id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoEventListener {

    /** Forca uma falha controlada quando o titulo comeca com este prefixo. Usado em testes de DLQ. */
    private static final String FAIL_PREFIX = "!fail";

    private final ProcessedMessageRepository processedMessageRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event,
                              @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        process(event, messageId);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event,
                              @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        process(event, messageId);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event,
                              @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        process(event, messageId);
    }

    private void process(TodoEvent event, String messageId) {
        // Sem messageId, dedupe nao tem como funcionar — processa direto.
        // Em runtime real isso nao acontece (OutboxPublisher sempre publica com id).
        if (messageId == null || messageId.isBlank()) {
            log.warn("[NOTIFICATION] msg sem messageId — dedupe desabilitada action={} todoId={}",
                    event.action(), event.todoId());
            doWork(event);
            return;
        }

        if (processedMessageRepository.existsById(messageId)) {
            log.info("[DEDUPE] descartada msg ja processada messageId={} action={} todoId={}",
                    messageId, event.action(), event.todoId());
            return;
        }

        doWork(event);

        boolean inserted = processedMessageRepository.tryInsert(messageId);
        if (!inserted) {
            log.warn("[DEDUPE] race detectada — outra thread tambem processou messageId={}", messageId);
        }
    }

    private void doWork(TodoEvent event) {
        // Hook de teste: titulo iniciado com "!fail" forca exception pra exercitar retry+DLQ.
        if (event.title() != null && event.title().startsWith(FAIL_PREFIX)) {
            throw new IllegalStateException("Falha simulada por prefixo '" + FAIL_PREFIX + "'");
        }
        log.info("[NOTIFICATION] Todo {} -> id={} | title='{}' | em={}",
                event.action(), event.todoId(), event.title(), event.occurredAt());
    }
}
