package com.javanauta.todo_app.controller;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.create(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponseDTO<TodoResponseDTO>> list(
            @ModelAttribute TodoFilterDTO filter,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(todoService.findAll(filter, pageable));
    }

    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listWithCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(todoService.listWithCursor(cursor, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> update(
            @PathVariable Long id,
            @RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.ok(todoService.update(id, request));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponseDTO> complete(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.complete(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
