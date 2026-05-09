package com.microservices.notification.listener;

import com.microservices.notification.config.RabbitMQConfig;
import com.microservices.notification.event.TodoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// @Slf4j gera automaticamente um campo `log` (via Lombok) para registrar mensagens
// @Component registra esta classe como bean gerenciado pelo Spring
@Slf4j
@Component
public class TodoEventListener {

    // @RabbitListener fica escutando a fila informada.
    // Quando uma mensagem chega, o Spring desserializa o JSON para TodoEvent e chama o método.
    @RabbitListener(queues = RabbitMQConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event) {
        // {} são placeholders do SLF4J: substituídos pelos argumentos em ordem,
        // sem concatenação de string quando o nível de log estiver desativado.
        log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event) {
        log.info("[NOTIFICATION] Todo ATUALIZADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event) {
        log.info("[NOTIFICATION] Todo DELETADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }
}
