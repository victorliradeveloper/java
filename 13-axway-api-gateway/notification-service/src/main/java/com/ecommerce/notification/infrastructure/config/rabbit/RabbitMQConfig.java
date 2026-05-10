package com.ecommerce.notification.infrastructure.config.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.payment-processed.queue}")
    private String paymentProcessedQueue;

    @Value("${rabbitmq.payment-processed.routing-key}")
    private String paymentProcessedRoutingKey;

    @Value("${rabbitmq.payment-failed.queue}")
    private String paymentFailedQueue;

    @Value("${rabbitmq.payment-failed.routing-key}")
    private String paymentFailedRoutingKey;

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue paymentProcessedQueue() {
        return QueueBuilder.durable(paymentProcessedQueue).build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(paymentFailedQueue).build();
    }

    @Bean
    public Binding paymentProcessedBinding() {
        return BindingBuilder.bind(paymentProcessedQueue()).to(ecommerceExchange()).with(paymentProcessedRoutingKey);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue()).to(ecommerceExchange()).with(paymentFailedRoutingKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
