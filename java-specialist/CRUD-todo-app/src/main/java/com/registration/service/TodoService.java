package com.registration.service;

import com.registration.dto.request.TodoRequestDTO;
import com.registration.dto.request.TodoUpdateDTO;
import com.registration.dto.response.TodoResponseDTO;
import com.registration.infrastructure.entity.Todo;
import com.registration.infrastructure.repository.TodoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        // toda tarefa começa como não concluída; o cliente altera via PUT /todos/{id}
        Todo todo = Todo.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .completed(false)
                .build();
        return toResponse(todoRepository.save(todo));
    }

    public List<TodoResponseDTO> findAll() {
        return todoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoResponseDTO findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        Todo todo = getOrThrow(id);
        if (dto.getTitle() != null) todo.setTitle(dto.getTitle());
        if (dto.getDescription() != null) todo.setDescription(dto.getDescription());
        if (dto.getCompleted() != null) todo.setCompleted(dto.getCompleted());
        return toResponse(todoRepository.save(todo));
    }

    public void delete(String id) {
        todoRepository.delete(getOrThrow(id));
    }

    private Todo getOrThrow(String id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Todo not found: " + id));
    }

    private TodoResponseDTO toResponse(Todo todo) {
        return TodoResponseDTO.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.isCompleted())
                .createdAt(todo.getCreatedAt())
                .build();
    }
}
