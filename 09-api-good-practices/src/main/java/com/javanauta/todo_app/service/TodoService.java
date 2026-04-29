package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.repository.TodoRepository;
import com.javanauta.todo_app.specification.TodoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoResponseDTO create(TodoRequestDTO request) {
        Todo todo = Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .build();
        return toResponse(todoRepository.save(todo));
    }

    public PagedResponseDTO<TodoResponseDTO> findAll(TodoFilterDTO filter, Pageable pageable) {
        return toPagedResponse(todoRepository.findAll(TodoSpecification.withFilters(filter), pageable));
    }

    public CursorPageResponseDTO<TodoResponseDTO> listWithCursor(Long cursor, int size) {
        List<Todo> todos = todoRepository.findWithCursor(cursor, PageRequest.of(0, size + 1));
        boolean hasNext = todos.size() > size;
        List<Todo> content = hasNext ? todos.subList(0, size) : todos;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;
        return CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(content.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    public TodoResponseDTO getById(Long id) {
        return toResponse(findEntity(id));
    }

    public TodoResponseDTO update(Long id, TodoRequestDTO request) {
        Todo todo = findEntity(id);
        todo.setTitle(request.getTitle());
        todo.setDescription(request.getDescription());
        todo.setDueDate(request.getDueDate());
        return toResponse(todoRepository.save(todo));
    }

    public TodoResponseDTO complete(Long id) {
        Todo todo = findEntity(id);
        todo.setCompleted(true);
        return toResponse(todoRepository.save(todo));
    }

    public void delete(Long id) {
        findEntity(id);
        todoRepository.deleteById(id);
    }

    private Todo findEntity(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    private PagedResponseDTO<TodoResponseDTO> toPagedResponse(Page<Todo> page) {
        return PagedResponseDTO.<TodoResponseDTO>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private TodoResponseDTO toResponse(Todo todo) {
        return TodoResponseDTO.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.isCompleted())
                .createdAt(todo.getCreatedAt())
                .dueDate(todo.getDueDate())
                .build();
    }
}
