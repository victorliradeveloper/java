package com.example.emailservice.application.email;

import com.example.emailservice.infrastructure.template.OrderCreatedEmailTemplate;
import com.example.emailservice.infrastructure.template.PasswordResetEmailTemplate;
import com.example.emailservice.infrastructure.template.UserLoginEmailTemplate;
import com.example.emailservice.infrastructure.template.UserRegisteredEmailTemplate;
import com.example.emailservice.infrastructure.messaging.OrderEventDTO;
import com.example.emailservice.infrastructure.messaging.UserEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRegisteredEmailTemplate registeredTemplate;
    private final UserLoginEmailTemplate loginTemplate;
    private final OrderCreatedEmailTemplate orderTemplate;
    private final PasswordResetEmailTemplate passwordTemplate;

    @Value("${mail.from}")
    private String from;

    public void sendUserRegistered(UserEventDTO.Payload payload) {
        send(payload.email(), registeredTemplate.subject(payload), registeredTemplate.body(payload));
    }

    public void sendUserLogin(UserEventDTO.Payload payload) {
        send(payload.email(), loginTemplate.subject(payload), loginTemplate.body(payload));
    }

    public void sendOrderCreated(OrderEventDTO.Payload payload) {
        send(payload.email(), orderTemplate.subject(payload), orderTemplate.body(payload));
    }

    public void sendPasswordReset(UserEventDTO.Payload payload) {
        send(payload.email(), passwordTemplate.subject(payload), passwordTemplate.body(payload));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email sent to={} subject=\"{}\"", to, subject);
    }
}
