package com.microservices.todo.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "todo.exchange";
    public static final String QUEUE_CREATED  = "todo.created.queue";
    public static final String QUEUE_UPDATED  = "todo.updated.queue";
    public static final String QUEUE_DELETED  = "todo.deleted.queue";

    public static final String ROUTING_CREATED = "todo.created";
    public static final String ROUTING_UPDATED  = "todo.updated";
    public static final String ROUTING_DELETED  = "todo.deleted";

    public static final String DLX = "todo.dlx";

    @Bean
    public TopicExchange todoExchange() {
        return new TopicExchange(EXCHANGE);
    }

    // Args precisam ser identicos aos do consumer (notification-service): RabbitMQ
    // retorna PRECONDITION_FAILED se um lado declara com x-dead-letter-exchange e
    // o outro sem. Topologia da DLX (exchange + DLQs + bindings) e' declarada no
    // consumer — aqui so' garantimos o match dos args da fila principal.
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
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
