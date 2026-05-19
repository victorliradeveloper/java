package com.microservices.notification.listener;

import com.microservices.notification.event.TodoEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// @Slf4j gera automaticamente um campo `log` (via Lombok) para registrar mensagens
// @Component registra esta classe como bean gerenciado pelo Spring
@Slf4j
@Component
public class TodoEventListener {

    // @SqsListener fica escutando a fila SQS informada (pelo nome).
    // Quando uma mensagem chega, o Spring Cloud AWS desserializa o corpo JSON
    // para TodoEvent (Jackson) e chama o método. Após retornar sem erro,
    // a mensagem é deletada da fila automaticamente.
    @SqsListener("todo-created-queue")
    public void onTodoCreated(TodoEvent event) {
        // {} são placeholders do SLF4J: substituídos pelos argumentos em ordem,
        // sem concatenação de string quando o nível de log estiver desativado.
        log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }

    @SqsListener("todo-updated-queue")
    public void onTodoUpdated(TodoEvent event) {
        log.info("[NOTIFICATION] Todo ATUALIZADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }

    @SqsListener("todo-deleted-queue")
    public void onTodoDeleted(TodoEvent event) {
        log.info("[NOTIFICATION] Todo DELETADO -> id={} | title='{}' | em={}", event.todoId(), event.title(), event.occurredAt());
    }
}
