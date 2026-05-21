package com.microservices.notification.listener;

import com.microservices.notification.config.SqsConfig;
import com.microservices.notification.event.TodoEvent;
import com.microservices.notification.service.EmailService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodoEventListener {

    private final EmailService emailService;

    @SqsListener(SqsConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event) {
        log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }

    @SqsListener(SqsConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event) {
        log.info("[NOTIFICATION] Todo ATUALIZADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }

    @SqsListener(SqsConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event) {
        log.info("[NOTIFICATION] Todo DELETADO -> id={} | title='{}' | em={}",
                event.todoId(), event.title(), event.occurredAt());
        emailService.send(event);
    }
}
