package com.microservices.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.mail")
public record NotificationMailProperties(String from, String to) {}
