package com.microservices.todo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Configuracao de publicacao de eventos via SNS (fan-out pattern).
 *
 * O todo-service publica eventos de dominio num unico topic SNS (todo-events)
 * em vez de em filas SQS individuais. O fan-out eh feito no LocalStack
 * (init-aws.sh) via subscriptions com FilterPolicy por action — notification
 * consome 3 filas filtradas, audit-service consome uma fila sem filtro.
 *
 * Beneficios:
 * - Publisher desacoplado: nao conhece os consumers.
 * - Novos consumers se inscrevem no topic sem mudanca no publisher.
 * - FilterPolicy reduz trafego: filas so recebem eventos que interessam.
 *
 * Ver .spec/03-patterns/fan-out.md.
 */
@Configuration
public class MessagingConfig {

    public static final String TOPIC_TODO_EVENTS = "todo-events";

    /**
     * SnsTemplate com MessageConverter Jackson reusando o ObjectMapper do Spring Boot
     * (com JavaTimeModule registrado, necessario para LocalDateTime em TodoEvent).
     *
     * MappingJackson2MessageConverter por default NAO inclui header de JavaType — entao
     * nao precisa do .doNotSendPayloadTypeHeader() que o SQS exigia.
     */
    @Bean
    public SnsTemplate snsTemplate(SnsClient snsClient, ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setSerializedPayloadClass(String.class);
        converter.setStrictContentTypeMatch(false);
        return new SnsTemplate(snsClient, converter);
    }
}
