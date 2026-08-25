package com.microservices.todo.downstream;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign que resolve {@code audit-service} via Eureka (load balancer).
 *
 * <p>Substituto do {@code outboxService.record(...)} da versao com mensageria.
 * Em vez de gravar num outbox local pra ser publicado depois, faz a chamada
 * HTTP direta — request bloqueia ate o audit-service responder.
 *
 * <p>Resilience4j ({@code @CircuitBreaker} + {@code @Retry} + fallback) eh
 * aplicado no servico que chama esse client, nao no proprio Feign — ver
 * {@link DownstreamNotifier}.
 */
@FeignClient(name = "audit-service")
public interface AuditClient {

    @PostMapping("/audit-logs")
    ResponseEntity<Void> recordEvent(@RequestBody TodoEventPayload event);
}
