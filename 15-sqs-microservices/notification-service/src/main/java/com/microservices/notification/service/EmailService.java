package com.microservices.notification.service;

import com.microservices.notification.config.NotificationMailProperties;
import com.microservices.notification.event.TodoEvent;
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

    // Nome da instancia Resilience4j (vinculada via @CircuitBreaker(name=...) e @Retry(name=...)).
    // Configurada em application.yml -> resilience4j.{circuitbreaker,retry}.instances.smtp.
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

    /**
     * Envia o email do evento. Protegido por Circuit Breaker + Retry.
     *
     * <h3>Comportamento sob falha</h3>
     * <ul>
     *   <li><b>SMTP transiente</b> (timeout, 4xx soft): {@code @Retry} tenta novamente
     *       com backoff exponencial. Sucesso eventual = transparente pro chamador.</li>
     *   <li><b>SMTP sustentado</b>: depois de N falhas o {@code @CircuitBreaker} abre.
     *       Chamadas seguintes lancam {@code CallNotPermittedException} imediatamente
     *       (fail-fast) sem nem tentar SMTP — protege o servidor de retry storm.</li>
     *   <li><b>CB OPEN -&gt; HALF_OPEN -&gt; CLOSED</b>: apos o cooldown, CB permite
     *       N chamadas de teste. Se sucedem, CB fecha; se falham, abre de novo.</li>
     * </ul>
     *
     * <h3>Interacao com SQS</h3>
     * Qualquer excecao (EmailDeliveryException ou CallNotPermittedException) propaga
     * pro listener; o {@code @SqsListener} nao acka a mensagem — SQS reentrega.
     * Apos 3 tentativas falhas (maxReceiveCount), mensagem cai na DLQ.
     *
     * @throws EmailDeliveryException                                            erro real do SMTP
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException   circuito aberto
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
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
