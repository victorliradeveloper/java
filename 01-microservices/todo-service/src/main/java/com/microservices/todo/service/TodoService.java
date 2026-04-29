package com.microservices.todo.service;

import com.microservices.todo.config.RabbitMQConfig;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo todo = Todo.builder()
                .title(dto.title())
                .description(dto.description())
                .completed(false)
                .build();
        TodoResponseDTO response = toResponse(repository.save(todo));
        publish(RabbitMQConfig.ROUTING_CREATED, TodoEvent.of(response.id(), response.title(), "CREATED"));
        return response;
    }

    public List<TodoResponseDTO> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoResponseDTO findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        Todo todo = getOrThrow(id);
        if (dto.title() != null) todo.setTitle(dto.title());
        if (dto.description() != null) todo.setDescription(dto.description());
        if (dto.completed() != null) todo.setCompleted(dto.completed());
        TodoResponseDTO response = toResponse(repository.save(todo));
        publish(RabbitMQConfig.ROUTING_UPDATED, TodoEvent.of(response.id(), response.title(), "UPDATED"));
        return response;
    }

    public void delete(String id) {
        Todo todo = getOrThrow(id);
        repository.delete(todo);
        publish(RabbitMQConfig.ROUTING_DELETED, TodoEvent.of(todo.getId(), todo.getTitle(), "DELETED"));
    }

    private void publish(String routingKey, TodoEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
    }

    private Todo getOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Todo not found: " + id));
    }

    private TodoResponseDTO toResponse(Todo todo) {
        return new TodoResponseDTO(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.isCompleted(),
                todo.getCreatedAt()
        );
    }
}
