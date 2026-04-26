package com.microservices.todo.event;

import java.time.LocalDateTime;

public record TodoEvent(
        String todoId,
        String title,
        String action,
        LocalDateTime occurredAt
) {
    public static TodoEvent of(String todoId, String title, String action) {
        return new TodoEvent(todoId, title, action, LocalDateTime.now());
    }
}
