package com.microservices.todo.service;

import com.microservices.todo.config.RabbitMQConfig;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
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
    private final TodoMapper mapper;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo todo = repository.save(mapper.toEntity(dto));
        TodoResponseDTO response = mapper.toResponse(todo);
        publish(RabbitMQConfig.ROUTING_CREATED, TodoEvent.of(response.id(), response.title(), "CREATED"));
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
        mapper.updateEntity(dto, todo);
        TodoResponseDTO response = mapper.toResponse(repository.save(todo));
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
}
