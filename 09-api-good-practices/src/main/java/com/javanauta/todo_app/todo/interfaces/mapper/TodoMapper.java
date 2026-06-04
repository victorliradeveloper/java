package com.javanauta.todo_app.todo.interfaces.mapper;

import com.javanauta.todo_app.todo.domain.model.Todo;
import com.javanauta.todo_app.todo.domain.model.TodoFilter;
import com.javanauta.todo_app.todo.domain.model.TodoPage;
import com.javanauta.todo_app.todo.interfaces.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.todo.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.shared.web.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.shared.web.dto.PagedResponseDTO;
import com.javanauta.todo_app.todo.interfaces.dto.response.TodoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TodoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    Todo toEntity(TodoRequestDTO request);

    TodoFilter toFilter(TodoFilterDTO dto);

    TodoResponseDTO toResponse(Todo todo);

    List<TodoResponseDTO> toResponseList(List<Todo> todos);

    default PagedResponseDTO<TodoResponseDTO> toPagedResponse(Page<Todo> page) {
        return PagedResponseDTO.<TodoResponseDTO>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    default CursorPageResponseDTO<TodoResponseDTO> toCursorResponse(TodoPage result) {
        return CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(toResponseList(result.content()))
                .nextCursor(result.nextCursor())
                .hasNext(result.hasNext())
                .build();
    }
}
