package com.example.emailservice.infrastructure.messaging;

import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String REGISTERED_QUEUE = "email.registered.queue";
    public static final String LOGIN_QUEUE      = "email.login.queue";
    public static final String ORDER_QUEUE      = "email.order.queue";
    public static final String PASSWORD_QUEUE   = "email.password.queue";

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
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // usa o tipo do parâmetro do @RabbitListener em vez do __TypeId__ header
        // isso evita conflito entre as classes do user-service e do email-service
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
