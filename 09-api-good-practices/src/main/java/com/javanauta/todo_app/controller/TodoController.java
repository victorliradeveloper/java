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
    public ResponseEntity<TodoResponseDTO> criar(@RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.criar(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponseDTO<TodoResponseDTO>> listar(
            @ModelAttribute TodoFilterDTO filtro,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(todoService.listar(filtro, pageable));
    }

    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listarComCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(todoService.listarComCursor(cursor, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.ok(todoService.atualizar(id, request));
    }

    @PatchMapping("/{id}/concluir")
    public ResponseEntity<TodoResponseDTO> concluir(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.concluir(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        todoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
