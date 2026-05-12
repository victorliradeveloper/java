package com.javanauta.todo_app.interfaces.rest;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.in.TodoUseCase;
import com.javanauta.todo_app.interfaces.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.TodoResponseDTO;
import com.javanauta.todo_app.interfaces.mapper.TodoMapper;
import com.javanauta.todo_app.interfaces.rest.docs.TodoApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController implements TodoApi {

    private final TodoUseCase todoUseCase;
    private final TodoMapper todoMapper;

    @PostMapping
    @Override
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        Todo saved = todoUseCase.create(getAuthenticatedUser(), todoMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(todoMapper.toResponse(saved));
    }

    @GetMapping
    @Override
    public ResponseEntity<PagedResponseDTO<TodoResponseDTO>> list(
            @ModelAttribute TodoFilterDTO filterDTO,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        TodoFilter filter = todoMapper.toFilter(filterDTO);
        Page<Todo> page = todoUseCase.findAll(getAuthenticatedUser(), filter, pageable);
        return ResponseEntity.ok(todoMapper.toPagedResponse(page));
    }

    @GetMapping("/cursor")
    @Override
    public ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listWithCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        TodoPage result = todoUseCase.listWithCursor(getAuthenticatedUser(), cursor, size);
        return ResponseEntity.ok(todoMapper.toCursorResponse(result));
    }

    @GetMapping("/{id}")
    @Override
    public ResponseEntity<TodoResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(todoMapper.toResponse(todoUseCase.getById(getAuthenticatedUser(), id)));
    }

    @PutMapping("/{id}")
    @Override
    public ResponseEntity<TodoResponseDTO> update(@PathVariable Long id, @RequestBody @Valid TodoRequestDTO request) {
        Todo updated = todoUseCase.update(getAuthenticatedUser(), id, todoMapper.toEntity(request));
        return ResponseEntity.ok(todoMapper.toResponse(updated));
    }

    @PatchMapping("/{id}/complete")
    @Override
    public ResponseEntity<TodoResponseDTO> complete(@PathVariable Long id) {
        return ResponseEntity.ok(todoMapper.toResponse(todoUseCase.complete(getAuthenticatedUser(), id)));
    }

    @DeleteMapping("/{id}")
    @Override
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        todoUseCase.delete(getAuthenticatedUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
