package com.microservices.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import com.microservices.notification.event.TodoEvent;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Topologia AMQP do notification-service.
 *
 * <p><b>Filas principais</b> bindadas em {@code todo.exchange} com as routing keys
 * {@code todo.{created,updated,deleted}}. Cada uma tem {@code x-dead-letter-exchange}
 * apontando pra {@link #DLX} — quando o Spring AMQP rejeita uma msg sem requeue
 * (depois de esgotar {@code spring.rabbitmq.listener.simple.retry.max-attempts}),
 * o RabbitMQ a roteia pela DLX usando a routing key original.
 *
 * <p><b>Dead Letter Exchange + DLQs</b> declaradas aqui pra que as msgs estouradas
 * tenham pra onde ir. Bindings espelhados: msg rejeitada da fila {@code todo.created.queue}
 * vai pra DLQ {@code todo.created.dlq} via routing key {@code todo.created}.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "todo.exchange";

    public static final String QUEUE_CREATED = "todo.created.queue";
    public static final String QUEUE_UPDATED = "todo.updated.queue";
    public static final String QUEUE_DELETED = "todo.deleted.queue";

    public static final String ROUTING_CREATED = "todo.created";
    public static final String ROUTING_UPDATED = "todo.updated";
    public static final String ROUTING_DELETED = "todo.deleted";

    public static final String DLX = "todo.dlx";
    public static final String DLQ_CREATED = "todo.created.dlq";
    public static final String DLQ_UPDATED = "todo.updated.dlq";
    public static final String DLQ_DELETED = "todo.deleted.dlq";

    @Bean
    public TopicExchange todoExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange todoDlx() {
        return new TopicExchange(DLX);
    }

    // x-dead-letter-exchange aplica em msgs rejeitadas sem requeue.
    // x-dead-letter-routing-key garante que a routing key original eh preservada
    // (sem esse arg, RabbitMQ usa a routing key com que a msg foi recebida — o que
    // tambem funciona aqui, mas o explicit eh mais defensivo a refactors).
    @Bean
    public Queue createdQueue() {
        return QueueBuilder.durable(QUEUE_CREATED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_CREATED)
                .build();
    }

    @Bean
    public Queue updatedQueue() {
        return QueueBuilder.durable(QUEUE_UPDATED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_UPDATED)
                .build();
    }

    @Bean
    public Queue deletedQueue() {
        return QueueBuilder.durable(QUEUE_DELETED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DELETED)
                .build();
    }

    @Bean public Queue createdDlq() { return QueueBuilder.durable(DLQ_CREATED).build(); }
    @Bean public Queue updatedDlq() { return QueueBuilder.durable(DLQ_UPDATED).build(); }
    @Bean public Queue deletedDlq() { return QueueBuilder.durable(DLQ_DELETED).build(); }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder.bind(createdQueue()).to(todoExchange()).with(ROUTING_CREATED);
    }

    @Bean
    public Binding updatedBinding() {
        return BindingBuilder.bind(updatedQueue()).to(todoExchange()).with(ROUTING_UPDATED);
    }

    @Bean
    public Binding deletedBinding() {
        return BindingBuilder.bind(deletedQueue()).to(todoExchange()).with(ROUTING_DELETED);
    }

    @Bean
    public Binding createdDlqBinding() {
        return BindingBuilder.bind(createdDlq()).to(todoDlx()).with(ROUTING_CREATED);
    }

    @Bean
    public Binding updatedDlqBinding() {
        return BindingBuilder.bind(updatedDlq()).to(todoDlx()).with(ROUTING_UPDATED);
    }

    @Bean
    public Binding deletedDlqBinding() {
        return BindingBuilder.bind(deletedDlq()).to(todoDlx()).with(ROUTING_DELETED);
    }

    /**
     * Converter customizado pra resolver o {@code __TypeId__} do publisher
     * (FQN {@code com.microservices.todo.event.TodoEvent}) pra classe local
     * do consumer ({@code com.microservices.notification.event.TodoEvent}).
     *
     * <p>Spring AMQP 3.x exige que toda classe referenciada via {@code __TypeId__}
     * esteja em um pacote "trusted" OU mapeada explicitamente via {@code idClassMapping}.
     * Mapear explicitamente eh mais seguro (whitelist tipada) do que abrir os
     * pacotes do publisher pra trust geral. Tambem cobre o caso de schemas
     * iguais com FQNs diferentes — padrao comum em microservicos.
     *
     * <p>{@code TypePrecedence.INFERRED} adiciona uma defesa extra: se ainda
     * cair um payload sem {@code __TypeId__}, o tipo eh inferido do parametro
     * do {@code @RabbitListener}.
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
