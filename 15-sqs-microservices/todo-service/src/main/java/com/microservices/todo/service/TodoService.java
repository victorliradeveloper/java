package com.microservices.todo.service;

import com.microservices.todo.config.MessagingConfig;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.exception.TodoNotFoundException;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
import com.microservices.todo.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;
    private final OutboxService outboxService;
    private final TodoMapper mapper;

    @Transactional
    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo entity = mapper.toEntity(dto);
        entity.setId(UUID.randomUUID().toString());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        Todo todo = repository.save(entity);
        TodoResponseDTO response = mapper.toResponse(todo);
        outboxService.record(
                MessagingConfig.TOPIC_TODO_EVENTS,
                response.id(),
                "CREATED",
                TodoEvent.of(response.id(), response.title(), "CREATED")
        );
        return response;
    }

    public List<TodoResponseDTO> findAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    public TodoResponseDTO findById(String id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        Todo todo = getOrThrow(id);
        TodoSnapshot before = TodoSnapshot.from(todo);
        mapper.updateEntity(dto, todo);
        TodoSnapshot after = TodoSnapshot.from(todo);

        // Bump updatedAt apenas em mudanca real — PUT no-op nao mexe no campo,
        // preservando idempotencia (igual ao evento UPDATED que so dispara em diff).
        if (!before.equals(after)) {
            todo.setUpdatedAt(LocalDateTime.now());
        }

        TodoResponseDTO response = mapper.toResponse(repository.save(todo));
        if (!before.equals(after)) {
            outboxService.record(
                    MessagingConfig.TOPIC_TODO_EVENTS,
                    response.id(),
                    "UPDATED",
                    TodoEvent.of(response.id(), response.title(), "UPDATED")
            );
        }
        return response;
    }

    @Transactional
    public void delete(String id) {
        repository.findById(id).ifPresent(todo -> {
            repository.delete(todo);
            outboxService.record(
                    MessagingConfig.TOPIC_TODO_EVENTS,
                    todo.getId(),
                    "DELETED",
                    TodoEvent.of(todo.getId(), todo.getTitle(), "DELETED")
            );
        });
    }

    private Todo getOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    private record TodoSnapshot(String title, String description, boolean completed) {
        static TodoSnapshot from(Todo todo) {
            return new TodoSnapshot(todo.getTitle(), todo.getDescription(), todo.isCompleted());
        }
    }
}
