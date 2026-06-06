package com.microservices.todo.service;

import com.microservices.todo.config.RabbitMQConfig;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.event.TodoEvent;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
import com.microservices.todo.outbox.OutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private static final String AGGREGATE_TYPE = "Todo";

    private final TodoRepository repository;
    private final OutboxService outboxService;
    private final TodoMapper mapper;

    /**
     * Cria um Todo e registra o evento {@code todo.created} no outbox dentro da
     * mesma transacao. Save da entidade + insert no outbox commitam juntos ou
     * nenhum dos dois — sem janela de inconsistencia entre "salvo mas nao publicado".
     */
    @Transactional
    public TodoResponseDTO create(TodoRequestDTO dto) {
        log.info("create dto={}", dto);
        Todo todo = repository.save(mapper.toEntity(dto));
        log.info("[TODO] persistido id={} title='{}' createdAt={}",
                todo.getId(), todo.getTitle(), todo.getCreatedAt());
        TodoResponseDTO response = mapper.toResponse(todo);
        outboxService.record(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_CREATED,
                response.id(),
                AGGREGATE_TYPE,
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
        mapper.updateEntity(dto, todo);
        TodoResponseDTO response = mapper.toResponse(repository.save(todo));
        outboxService.record(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_UPDATED,
                response.id(),
                AGGREGATE_TYPE,
                "UPDATED",
                TodoEvent.of(response.id(), response.title(), "UPDATED")
        );
        return response;
    }

    @Transactional
    public void delete(String id) {
        Todo todo = getOrThrow(id);
        repository.delete(todo);
        outboxService.record(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_DELETED,
                todo.getId(),
                AGGREGATE_TYPE,
                "DELETED",
                TodoEvent.of(todo.getId(), todo.getTitle(), "DELETED")
        );
    }

    private Todo getOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Todo not found: " + id));
    }
}
