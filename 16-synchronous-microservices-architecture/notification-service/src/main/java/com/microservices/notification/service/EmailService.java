package com.microservices.notification.service;

import com.microservices.notification.config.NotificationMailProperties;
import com.microservices.notification.dto.TodoEventDTO;
import com.microservices.notification.exception.EmailDeliveryException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

/**
 * Envia e-mail HTML para cada evento Todo recebido. Protegido por
 * Circuit Breaker + Retry (Resilience4j).
 *
 * <h3>Comportamento sob falha</h3>
 * <ul>
 *   <li><b>SMTP transiente</b>: {@code @Retry} tenta novamente com backoff
 *       exponencial (200ms, 400ms, 800ms). Sucesso eventual = transparente
 *       pro chamador.</li>
 *   <li><b>SMTP sustentado</b>: depois de N falhas em janela deslizante,
 *       o {@code @CircuitBreaker} abre. Chamadas seguintes lancam
 *       {@code CallNotPermittedException} imediatamente (fail-fast) sem nem tentar
 *       SMTP — protege o servidor de retry storm.</li>
 * </ul>
 *
 * <h3>Diferenca para a versao com mensageria</h3>
 * Na versao AMQP, exception aqui propagava pro listener -> Spring AMQP retry
 * esgotava -> DLQ via DLX. Aqui, exception propaga pro controller -> HTTP 500
 * pro chamador (todo-service), que decide via Resilience4j do lado dele
 * (fallback que NAO falha a operacao principal). Trade-off: o email pode
 * ser perdido se nao houver retry no chamador.
 */
@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String TEMPLATE_NAME = "email/todo-event";
    private static final String DEFAULT_HEADER_COLOR = "#0f172a";
    private static final String SUBJECT_FORMAT = "Todo %s: %s";

    // Cor do header por tipo de acao — verde criou, ambar atualizou, vermelho deletou.
    private static final Map<String, String> HEADER_COLOR = Map.of(
            "CREATED", "#16a34a",
            "UPDATED", "#d97706",
            "DELETED", "#dc2626"
    );

    /** Nome da instancia Resilience4j (vinculada via @CircuitBreaker/@Retry name=...). */
    static final String RESILIENCE_INSTANCE = "smtp";

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

    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public void send(TodoEventDTO event) {
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

    private String renderTemplate(TodoEventDTO event) {
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
