package com.javanauta.todo_app.interfaces.mapper;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.interfaces.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.TodoResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class TodoMapper {

    public Todo toEntity(TodoRequestDTO request) {
        return Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .build();
    }

    public TodoFilter toFilter(TodoFilterDTO dto) {
        return new TodoFilter(dto.getTitle(), dto.getCompleted(), dto.getDueDateFrom(), dto.getDueDateTo());
    }

    public TodoResponseDTO toResponse(Todo todo) {
        return TodoResponseDTO.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.isCompleted())
                .createdAt(todo.getCreatedAt())
                .dueDate(todo.getDueDate())
                .build();
    }

    public PagedResponseDTO<TodoResponseDTO> toPagedResponse(Page<Todo> page) {
        return PagedResponseDTO.<TodoResponseDTO>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public CursorPageResponseDTO<TodoResponseDTO> toCursorResponse(TodoPage result) {
        return CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(result.content().stream().map(this::toResponse).toList())
                .nextCursor(result.nextCursor())
                .hasNext(result.hasNext())
                .build();
    }
}
