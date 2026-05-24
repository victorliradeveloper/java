package com.microservices.todo.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do subsistema de outbox.
 *
 * <p>Habilita {@link OutboxProperties} e expoe a {@link BackoffPolicy} default
 * (exponencial com jitter). Outra implementacao pode substituir via {@code @Primary}
 * num teste ou perfil sem mexer no publisher.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    public BackoffPolicy outboxBackoffPolicy(OutboxProperties properties) {
        return new ExponentialJitterBackoffPolicy(properties.backoff());
    }
}
