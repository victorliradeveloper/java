package com.example.userservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "user.exchange";

    public static final String REGISTERED_QUEUE = "email.registered.queue";
    public static final String LOGIN_QUEUE      = "email.login.queue";
    public static final String ORDER_QUEUE      = "email.order.queue";
    public static final String PASSWORD_QUEUE   = "email.password.queue";

    public static final String REGISTERED_KEY = "user.registered";
    public static final String LOGIN_KEY      = "user.login";
    public static final String ORDER_KEY      = "order.created";
    public static final String PASSWORD_KEY   = "user.password";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue registeredQueue() {
        return QueueBuilder.durable(REGISTERED_QUEUE).build();
    }

    @Bean
    public Queue loginQueue() {
        return QueueBuilder.durable(LOGIN_QUEUE).build();
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE).build();
    }

    @Bean
    public Queue passwordQueue() {
        return QueueBuilder.durable(PASSWORD_QUEUE).build();
    }

    @Bean
    public Binding registeredBinding(TopicExchange exchange) {
        return BindingBuilder.bind(registeredQueue()).to(exchange).with(REGISTERED_KEY);
    }

    @Bean
    public Binding loginBinding(TopicExchange exchange) {
        return BindingBuilder.bind(loginQueue()).to(exchange).with(LOGIN_KEY);
    }

    @Bean
    public Binding orderBinding(TopicExchange exchange) {
        return BindingBuilder.bind(orderQueue()).to(exchange).with(ORDER_KEY);
    }

    @Bean
    public Binding passwordBinding(TopicExchange exchange) {
        return BindingBuilder.bind(passwordQueue()).to(exchange).with(PASSWORD_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
