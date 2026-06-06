package com.microservices.audit.config;

import com.microservices.audit.event.TodoEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Topologia AMQP do audit-service.
 *
 * <p><b>Uma fila so</b> ({@link #QUEUE_AUDIT}) recebe TODOS os eventos do dominio
 * Todo via {@code todo.#} (wildcard topic). Diferente do notification-service que
 * tem 3 filas (uma por acao), o audit grava tudo igual num log append-only.
 *
 * <p><b>DLX/DLQ separados</b> ({@link #DLX_AUDIT}/{@link #DLQ_AUDIT}) pra
 * isolacao: falhas no audit-service nao se misturam com falhas do notification.
 *
 * <p><b>Bind do consumer, nao do publisher</b>: o todo-service nao conhece o
 * audit-service. O audit declara sua propria fila e bindings na exchange
 * compartilhada {@code todo.exchange}.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "todo.exchange";
    public static final String QUEUE_AUDIT = "todo.audit.queue";
    public static final String ROUTING_PATTERN = "todo.#";

    public static final String DLX_AUDIT = "todo.audit.dlx";
    public static final String DLQ_AUDIT = "todo.audit.dlq";

    @Bean
    public TopicExchange todoExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange auditDlx() {
        return new TopicExchange(DLX_AUDIT);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(QUEUE_AUDIT)
                .withArgument("x-dead-letter-exchange", DLX_AUDIT)
                // sem x-dead-letter-routing-key → preserva a routing key original
                .build();
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(DLQ_AUDIT).build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(todoExchange()).with(ROUTING_PATTERN);
    }

    @Bean
    public Binding auditDlqBinding() {
        // Catch-all na DLX tambem.
        return BindingBuilder.bind(auditDlq()).to(auditDlx()).with("#");
    }

    /**
     * Converter customizado com {@code idClassMapping} mapeando o FQN do publisher
     * pra classe local do audit-service. Mesma logica do notification-service
     * — em microsservicos, schemas iguais com FQNs diferentes sao a norma.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(TypePrecedence.INFERRED);
        typeMapper.setIdClassMapping(Map.of(
                "com.microservices.todo.event.TodoEvent", TodoEvent.class
        ));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
