package com.microservices.notification.service;

import com.microservices.notification.config.NotificationMailProperties;
import com.microservices.notification.event.TodoEvent;
import com.microservices.notification.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String TEMPLATE_NAME = "email/todo-event";
    private static final String DEFAULT_HEADER_COLOR = "#0f172a";
    private static final String SUBJECT_FORMAT = "Todo %s: %s";

    // Cor do header por tipo de ação — verde criou, âmbar atualizou, vermelho deletou.
    private static final Map<String, String> HEADER_COLOR = Map.of(
            "CREATED", "#16a34a",
            "UPDATED", "#d97706",
            "DELETED", "#dc2626"
    );

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final NotificationMailProperties mailProps;

    public EmailService(JavaMailSender mailSender,
                        SpringTemplateEngine templateEngine,
                        NotificationMailProperties mailProps) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailProps = mailProps;
    }

    public void send(TodoEvent event) {
        try {
            String html = renderTemplate(event);
            String subject = SUBJECT_FORMAT.formatted(event.action(), event.title());
            dispatch(subject, html);
            log.info("[EMAIL] enviado para {} (action={}, todoId={})",
                    mailProps.to(), event.action(), event.todoId());
        } catch (MessagingException e) {
            log.error("[EMAIL] falha ao enviar para {} (action={}, todoId={}): {}",
                    mailProps.to(), event.action(), event.todoId(), e.getMessage());
            throw new EmailDeliveryException("Falha ao enviar e-mail para " + mailProps.to(), e);
        }
    }

    private String renderTemplate(TodoEvent event) {
        Context ctx = new Context();
        ctx.setVariable("todoId", event.todoId());
        ctx.setVariable("title", event.title());
        ctx.setVariable("action", event.action());
        ctx.setVariable("occurredAt", event.occurredAt().format(FMT));
        ctx.setVariable("headerColor", HEADER_COLOR.getOrDefault(event.action(), DEFAULT_HEADER_COLOR));
        return templateEngine.process(TEMPLATE_NAME, ctx);
    }

    private void dispatch(String subject, String html) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
        helper.setFrom(mailProps.from());
        helper.setTo(mailProps.to());
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(mime);
    }
}
