package com.microservices.todo.downstream;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wrapper sobre os Feign clients que aplica {@code @CircuitBreaker} + {@code @Retry}
 * e centraliza a politica de FALLBACK: se o downstream esta indisponivel,
 * apenas logamos e seguimos — a operacao principal (Todo CRUD) NAO falha.
 *
 * <h3>Por que essa eh a escolha certa para audit/notification</h3>
 * <ul>
 *   <li><b>Audit</b>: util pra observabilidade mas nao bloqueante. Se o
 *       audit-service esta caido, ainda assim faz sentido criar o Todo.</li>
 *   <li><b>Notification</b>: email eh fire-and-forget na pratica — o usuario
 *       criou o Todo, ele quer ver 201, nao quer ver erro porque o SMTP caiu.</li>
 * </ul>
 *
 * <h3>Trade-off honesto</h3>
 * O fallback "loga e segue" perde eventos quando o downstream esta caido. Em
 * prod real, esse fallback gravaria num "dead-letter" interno (uma tabela)
 * pra replay manual ou automatico. Aqui eh didatico — o log explicita o
 * que esta sendo perdido.
 *
 * <h3>Comportamento sob falha (por instancia)</h3>
 * <pre>
 *   tentativa 1 falha -> Retry espera 200ms
 *   tentativa 2 falha -> Retry espera 400ms
 *   tentativa 3 falha -> propaga exception -> fallback eh chamado -> loga
 * </pre>
 * Quando o CircuitBreaker abre (apos N falhas), as proximas chamadas
 * vao DIRETO pro fallback sem nem tentar HTTP — fail-fast.
 *
 * <h3>Por que metodos separados (e nao um generico)?</h3>
 * O Resilience4j vincula CircuitBreaker e Retry por NOME — um nome por
 * downstream. Metodo generico significaria uma instancia so' compartilhada
 * (falha em audit derrubaria o CB de notification). Separar isola os blast
 * radius.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownstreamNotifier {

    private final AuditClient auditClient;
    private final NotificationClient notificationClient;

    @CircuitBreaker(name = "audit-service")
    @Retry(name = "audit-service", fallbackMethod = "auditFallback")
    public void notifyAudit(TodoEventPayload event) {
        log.debug("[AUDIT-CALL] enviando eventId={} action={} todoId={}",
                event.eventId(), event.action(), event.todoId());
        auditClient.recordEvent(event);
    }

    @CircuitBreaker(name = "notification-service")
    @Retry(name = "notification-service", fallbackMethod = "notificationFallback")
    public void notifyNotification(TodoEventPayload event) {
        log.debug("[NOTIFICATION-CALL] enviando eventId={} action={} todoId={}",
                event.eventId(), event.action(), event.todoId());
        notificationClient.sendNotification(event);
    }

    // Fallbacks: assinatura precisa bater com a do metodo original + um Throwable no final.
    @SuppressWarnings("unused")
    private void auditFallback(TodoEventPayload event, Throwable ex) {
        log.error("[AUDIT-FALLBACK] evento perdido eventId={} action={} todoId={} cause={}: {}",
                event.eventId(), event.action(), event.todoId(),
                ex.getClass().getSimpleName(), ex.getMessage());
    }

    @SuppressWarnings("unused")
    private void notificationFallback(TodoEventPayload event, Throwable ex) {
        log.error("[NOTIFICATION-FALLBACK] evento perdido eventId={} action={} todoId={} cause={}: {}",
                event.eventId(), event.action(), event.todoId(),
                ex.getClass().getSimpleName(), ex.getMessage());
    }
}
