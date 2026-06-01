package com.microservices.todo.service;

import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia transacional do Todo, isolada num bean proprio para que o
 * {@code @Transactional} funcione via proxy do Spring (chamada cross-bean).
 *
 * <p>Se estes metodos vivessem no {@link TodoService}, a invocacao via
 * {@code this.persist...} ignoraria o proxy e a transacao nem seria aberta.
 * Mantendo aqui, o {@link TodoService} chama outro bean e o interceptor
 * transacional roda normalmente.
 *
 * <p>O escopo intencionalmente <b>nao inclui</b> as chamadas HTTP aos
 * downstreams — chamadas remotas dentro de transacao bloqueariam a connection
 * pool durante o request remoto. Quem orquestra "persiste + notifica" eh o
 * {@link TodoService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoPersistenceService {

    private final TodoRepository repository;
    private final TodoMapper mapper;

    @Transactional
    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo todo = repository.save(mapper.toEntity(dto));
        log.info("[TODO] persistido id={} title='{}' createdAt={}",
                todo.getId(), todo.getTitle(), todo.getCreatedAt());
        return mapper.toResponse(todo);
    }

    @Transactional
    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        Todo todo = getOrThrow(id);
        mapper.updateEntity(dto, todo);
        return mapper.toResponse(repository.save(todo));
    }

    @Transactional
    public Todo delete(String id) {
        Todo todo = getOrThrow(id);
        repository.delete(todo);
        return todo;
    }

    @Transactional(readOnly = true)
    public Todo getOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Todo not found: " + id));
    }
}
