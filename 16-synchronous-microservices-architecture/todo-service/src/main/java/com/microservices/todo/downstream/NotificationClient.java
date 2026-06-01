package com.microservices.todo.downstream;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign que resolve {@code notification-service} via Eureka.
 *
 * <p>Trade-off importante vs versao com mensageria: o envio de email agora
 * acontece DENTRO do ciclo de vida do request HTTP do cliente final. Se o
 * SMTP esta lento, o cliente espera. Por isso o timeout no Feign + Circuit
 * Breaker no chamador sao criticos — sem isso, uma degradacao do SMTP
 * impacta a latencia da API.
 */
@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/notifications/todo-events")
    ResponseEntity<Void> sendNotification(@RequestBody TodoEventPayload event);
}
