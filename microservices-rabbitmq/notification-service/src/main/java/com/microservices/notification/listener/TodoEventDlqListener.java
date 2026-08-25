package com.microservices.notification.listener;

import com.microservices.notification.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listener das Dead Letter Queues. Loga WARN com o body bruto e os headers
 * de cada mensagem que falhou na fila principal e foi roteada pra DLQ.
 *
 * <p>Recebe {@link Message} (nao {@code TodoEvent}) porque a causa mais comum
 * de chegar aqui eh justamente payload malformado — tentar desserializar de
 * novo daria o mesmo erro em loop e a msg voltaria pra DLQ. {@code Message}
 * aceita qualquer body.
 *
 * <p>Em producao real este listener seria substituido por um alerta em
 * cima da metrica de mensagens na DLQ; as msgs ficariam retidas pra inspecao
 * manual + republish (shovel ou redrive). Aqui ele acka (auto-ack via retorno
 * sem exception) pra ter sinal imediato em dev sem reentrega cíclica.
 *
 * <h3>Headers uteis em msgs roteadas via DLX</h3>
 * O RabbitMQ adiciona {@code x-death} com informacao de quantas vezes a msg
 * foi rejeitada e por qual fila — ajuda no diagnostico.
 */
@Slf4j
@Component
public class TodoEventDlqListener {

    @RabbitListener(queues = RabbitMQConfig.DLQ_CREATED)
    public void onCreatedDlq(Message message) {
        logDlq(RabbitMQConfig.DLQ_CREATED, message);
    }

    @RabbitListener(queues = RabbitMQConfig.DLQ_UPDATED)
    public void onUpdatedDlq(Message message) {
        logDlq(RabbitMQConfig.DLQ_UPDATED, message);
    }

    @RabbitListener(queues = RabbitMQConfig.DLQ_DELETED)
    public void onDeletedDlq(Message message) {
        logDlq(RabbitMQConfig.DLQ_DELETED, message);
    }

    private void logDlq(String queue, Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        log.warn("[DLQ] {} -> messageId={} x-death={} body={}",
                queue,
                message.getMessageProperties().getMessageId(),
                xDeath,
                body);
    }
}
