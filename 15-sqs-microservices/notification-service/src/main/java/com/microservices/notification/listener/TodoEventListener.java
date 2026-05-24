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
    // MessageHeaders.ID padrao do Spring Messaging (como UUID).

    @SqsListener(SqsConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
        process(event, messageId);
    }

    @SqsListener(SqsConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
        process(event, messageId);
    }

    @SqsListener(SqsConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
        process(event, messageId);
    }

    /**
     * Fluxo:
     *   1. Consulta dedupe (read-only).
     *   2. Envia email — protegido por Circuit Breaker + Retry no EmailService.
     *   3. Marca como processado SOMENTE apos sucesso do envio.
     *
     * <p>Por que dedupe DEPOIS do send: garante "duplica raro" em vez de "perde raro".
     * Falha no SMTP ou CB OPEN propagam excecao -> @SqsListener nao acka -> SQS reentrega
     * -> proxima tentativa repete o fluxo -> apos sucesso ou maxReceiveCount=3 (DLQ).
     *
     * <p>Janela de duplicacao: se houver crash entre o send com sucesso e o tryInsert,
     * a proxima entrega vai mandar o email de novo. Janela tipica: &lt; 50ms. Aceitavel.
     *
     * <p>Race condition entre threads concorrentes (visibility timeout muito curto): a
     * checagem inicial pode falhar em ver entrada de outra thread em voo, mas o tryInsert
     * final retorna {@code false} no segundo, e o log WARN aponta a corrida — sem impacto
     * de correcao alem do email duplicado.
     */
    private void process(TodoEvent event, UUID messageId) {
        String id = messageId.toString();
        if (processedMessageRepository.existsById(id)) {
            log.info("[DEDUPE] mensagem ja processada, descartada messageId={} action={} todoId={}",
                    messageId, event.action(), event.todoId());
            return;
        }

        log.info("[NOTIFICATION] Todo {} -> id={} | title='{}' | em={}",
                event.action(), event.todoId(), event.title(), event.occurredAt());

        // Pode lancar EmailDeliveryException ou CallNotPermittedException — em
        // ambos os casos a mensagem nao eh ack'd e SQS reentrega.
        emailService.send(event);

        boolean inserted = processedMessageRepository.tryInsert(id);
        if (!inserted) {
            log.warn("[DEDUPE] race detectada — outra thread tambem enviou email pra messageId={}",
                    messageId);
        }
    }
}
