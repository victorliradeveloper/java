package com.microservices.todo.service;

import com.microservices.todo.config.SqsConfig;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.exception.TodoNotFoundException;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;
    private final SqsTemplate sqsTemplate;
    private final TodoMapper mapper;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo entity = mapper.toEntity(dto);
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(LocalDateTime.now());
        Todo todo = repository.save(entity);
        TodoResponseDTO response = mapper.toResponse(todo);
        publish(SqsConfig.QUEUE_CREATED, TodoEvent.of(response.id(), response.title(), "CREATED"));
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

    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        Todo todo = getOrThrow(id);
        TodoSnapshot before = TodoSnapshot.from(todo);
        mapper.updateEntity(dto, todo);
        TodoSnapshot after = TodoSnapshot.from(todo);

        TodoResponseDTO response = mapper.toResponse(repository.save(todo));
        if (!before.equals(after)) {
            publish(SqsConfig.QUEUE_UPDATED, TodoEvent.of(response.id(), response.title(), "UPDATED"));
        }
        return response;
    }

    public void delete(String id) {
        repository.findById(id).ifPresent(todo -> {
            repository.delete(todo);
            publish(SqsConfig.QUEUE_DELETED, TodoEvent.of(todo.getId(), todo.getTitle(), "DELETED"));
        });
    }

    private void publish(String queueName, TodoEvent event) {
        sqsTemplate.send(queueName, event);
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
