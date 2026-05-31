package com.ecommerce.notification.domain.port.out;

public interface EmailSender {
    void send(String to, String subject, String body);
}
