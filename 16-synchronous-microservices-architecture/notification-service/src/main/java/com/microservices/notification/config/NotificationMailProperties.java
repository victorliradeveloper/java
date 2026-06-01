package com.microservices.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereco de envio/recebimento de email. Vem do {@code .env} via docker-compose;
 * sem default pra forcar configuracao explicita (startup falha se faltar).
 */
@ConfigurationProperties("notification.mail")
public record NotificationMailProperties(String from, String to) {}
