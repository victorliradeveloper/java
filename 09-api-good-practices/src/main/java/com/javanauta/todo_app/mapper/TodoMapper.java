package com.javanauta.todo_app.mapper;

import com.javanauta.todo_app.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.dto.response.TodoResponseDTO;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.model.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TodoMapper {

    public Todo toEntity(TodoRequestDTO request, User user) {
        return Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .user(user)
                .build();
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

    public void updateEntity(TodoRequestDTO request, Todo todo) {
        todo.setTitle(request.getTitle());
        todo.setDescription(request.getDescription());
        todo.setDueDate(request.getDueDate());
    }

    public CursorPageResponseDTO<TodoResponseDTO> toCursorResponse(List<Todo> content, Long nextCursor, boolean hasNext) {
        return CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(content.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}
