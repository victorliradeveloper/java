package com.microservices.audit.listener;

import com.microservices.audit.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listener da Dead Letter Queue do audit-service.
 *
 * <p>Mensagens caem aqui quando: payload nao desserializa, INSERT falha
 * persistentemente (DB indisponivel), ou outro erro nao-transitorio exauriu
 * o retry. Em producao real, alarme em cima da metrica da DLQ + redrive
 * manual. Em dev, o auto-ack via retorno normal evita poluir o log com
 * reentregas ciclicas.
 *
 * <p>Recebe {@link Message} bruta porque a causa mais comum de chegar aqui
 * eh payload malformado — tentar desserializar de novo daria o mesmo erro.
 */
@Slf4j
@Component
public class TodoAuditDlqListener {

    @RabbitListener(queues = RabbitMQConfig.DLQ_AUDIT)
    public void onAuditDlq(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        log.warn("[AUDIT-DLQ] {} -> messageId={} x-death={} body={}",
                RabbitMQConfig.DLQ_AUDIT,
                message.getMessageProperties().getMessageId(),
                xDeath,
                body);
    }
}
