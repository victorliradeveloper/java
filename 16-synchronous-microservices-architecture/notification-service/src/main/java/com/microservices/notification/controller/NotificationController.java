package com.microservices.notification.controller;

import com.microservices.notification.dto.TodoEventDTO;
import com.microservices.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint sincrono que recebe eventos do todo-service.
 *
 * <p>Substituto do {@code @RabbitListener} da versao com mensageria. Mesma
 * semantica de idempotencia (via {@code eventId}), so muda o transporte:
 * HTTP request/response em vez de AMQP push.
 *
 * <p>Comportamento por status:
 * <ul>
 *   <li><b>202 Accepted</b>: evento aceito e processado (ou dedupado).</li>
 *   <li><b>500</b>: erro processando — caller (todo-service) trata via fallback.</li>
 * </ul>
 */
@RestController
@RequestMapping("/notifications/todo-events")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody @Valid TodoEventDTO dto) {
        notificationService.process(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
