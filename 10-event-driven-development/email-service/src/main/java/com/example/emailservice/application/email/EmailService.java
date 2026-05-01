package com.example.emailservice.application.email;

import com.example.emailservice.infrastructure.template.EmailTemplateFactory;
import com.example.emailservice.interfaces.dto.OrderEventDTO;
import com.example.emailservice.interfaces.dto.UserEventDTO;
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
    private final EmailTemplateFactory templateFactory;

    @Value("${mail.from}")
    private String from;

    public void sendUserRegistered(UserEventDTO.Payload payload) {
        send(payload.email(),
                templateFactory.registeredSubject(payload),
                templateFactory.registeredBody(payload));
    }

    public void sendUserLogin(UserEventDTO.Payload payload) {
        send(payload.email(),
                templateFactory.loginSubject(payload),
                templateFactory.loginBody(payload));
    }

    public void sendOrderCreated(OrderEventDTO.Payload payload) {
        send(payload.email(),
                templateFactory.orderSubject(payload),
                templateFactory.orderBody(payload));
    }

    public void sendPasswordReset(UserEventDTO.Payload payload) {
        send(payload.email(),
                templateFactory.passwordSubject(payload),
                templateFactory.passwordBody(payload));
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to={} subject=\"{}\"", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage(), e);
        }
    }
}
