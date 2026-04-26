package com.microservices.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "todo.exchange";
    public static final String QUEUE_CREATED = "todo.created.queue";
    public static final String QUEUE_UPDATED = "todo.updated.queue";
    public static final String QUEUE_DELETED = "todo.deleted.queue";

    @Bean
    public TopicExchange todoExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean public Queue createdQueue() { return new Queue(QUEUE_CREATED, true); }
    @Bean public Queue updatedQueue() { return new Queue(QUEUE_UPDATED, true); }
    @Bean public Queue deletedQueue() { return new Queue(QUEUE_DELETED, true); }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder.bind(createdQueue()).to(todoExchange()).with("todo.created");
    }

    @Bean
    public Binding updatedBinding() {
        return BindingBuilder.bind(updatedQueue()).to(todoExchange()).with("todo.updated");
    }

    @Bean
    public Binding deletedBinding() {
        return BindingBuilder.bind(deletedQueue()).to(todoExchange()).with("todo.deleted");
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
