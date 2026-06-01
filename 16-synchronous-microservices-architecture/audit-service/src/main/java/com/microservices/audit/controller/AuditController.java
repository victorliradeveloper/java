package com.microservices.audit.controller;

import com.microservices.audit.dto.TodoAuditEventDTO;
import com.microservices.audit.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint sincrono que recebe eventos de auditoria do todo-service.
 *
 * <p>Substituto do {@code @RabbitListener} da versao com mensageria. Mesma
 * semantica de idempotencia (via {@code eventId} como PK), so muda o
 * transporte: HTTP request/response em vez de AMQP push.
 *
 * <p>Sempre retorna 202 ACCEPTED (independente de ter inserido ou sido
 * dedupado) — pro caller a operacao foi aceita em ambos os casos.
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Void> recordEvent(@RequestBody @Valid TodoAuditEventDTO dto) {
        auditService.record(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
