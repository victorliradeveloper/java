package com.microservices.notification.service;

import com.microservices.notification.dto.TodoEventDTO;
import com.microservices.notification.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra dedupe + envio de e-mail para os eventos recebidos do todo-service.
 *
 * <h3>Por que dedupar DEPOIS de enviar o email (e nao antes)?</h3>
 * Igual a versao com mensageria. Se gravarmos {@code processed_events} ANTES de
 * mandar o email, uma falha no SMTP deixaria o evento marcado como processado
 * mas sem efeito visivel. Resultado: "perde raro". Marcando DEPOIS, no pior
 * caso retentamos e o email sai 2x — "duplica raro". Duplicar eh quase sempre
 * menos pior que perder.
 *
 * <h3>Hook de teste</h3>
 * Titulo iniciado com {@code !fail} forca exception pra exercitar o
 * fallback no chamador (todo-service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String FAIL_PREFIX = "!fail";

    private final ProcessedEventRepository processedEventRepository;
    private final EmailService emailService;

    public void process(TodoEventDTO event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("[DEDUPE] descartado evento ja processado eventId={} action={} todoId={}",
                    event.eventId(), event.action(), event.todoId());
            return;
        }

        if (event.title() != null && event.title().startsWith(FAIL_PREFIX)) {
            throw new IllegalStateException("Falha simulada por prefixo '" + FAIL_PREFIX + "'");
        }

        log.info("[NOTIFICATION] Todo {} -> id={} | title='{}' | em={}",
                event.action(), event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);

        boolean inserted = processedEventRepository.tryInsert(event.eventId());
        if (!inserted) {
            log.warn("[DEDUPE] race detectada — outra thread tambem processou eventId={}", event.eventId());
        }
    }
}
