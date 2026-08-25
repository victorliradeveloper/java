package com.example.emailservice.infrastructure.template;

public interface EmailTemplate<T> {
    String subject(T payload);
    String body(T payload);
}
