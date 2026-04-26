package com.microservices.todo.service;

import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo todo = Todo.builder()
                .title(dto.title())
                .description(dto.description())
                .completed(false)
                .build();
        return toResponse(repository.save(todo));
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
        return toResponse(repository.save(todo));
    }

    public void delete(String id) {
        repository.delete(getOrThrow(id));
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
