package com.microservices.todo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Constantes com os nomes das filas SQS e configuração do SqsTemplate.
 *
 * No RabbitMQ tínhamos uma TopicExchange com 3 routing keys. SQS não tem
 * exchange/routing key — cada ação publica direto na fila correspondente.
 * As filas são criadas pelo LocalStack via script localstack/init-aws.sh.
 *
 * O bean SqsTemplate é customizado para NÃO incluir o MessageAttribute
 * "JavaType" (FQN da classe do payload). Por padrão o Spring Cloud AWS
 * inclui esse header e o consumer tenta Class.forName(...) com o nome
 * recebido. Como o todo-service e o notification-service têm classes
 * TodoEvent em pacotes diferentes, isso falharia no consumer. Sem o
 * header, o consumer usa o tipo do parâmetro do método @SqsListener
 * para desserializar.
 */
@Configuration
public class SqsConfig {

    public static final String QUEUE_CREATED = "todo-created-queue";
    public static final String QUEUE_UPDATED = "todo-updated-queue";
    public static final String QUEUE_DELETED = "todo-deleted-queue";

    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configureDefaultConverter(converter -> {
                    if (converter instanceof SqsMessagingMessageConverter c) {
                        c.doNotSendPayloadTypeHeader();
                        // reusa o ObjectMapper auto-configurado pelo Spring Boot
                        // (com JavaTimeModule registrado, necessario para LocalDateTime).
                        c.setObjectMapper(objectMapper);
                    }
                })
                .build();
    }
}
