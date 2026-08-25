package com.javanauta.todo_app.controller;

import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/todo")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    public ResponseEntity<TodoResponseDTO> criar(@RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.criar(request));
    }

    @GetMapping
    public ResponseEntity<List<TodoResponseDTO>> listar(
            @RequestParam(required = false) Boolean concluido) {
        if (concluido != null) {
            return ResponseEntity.ok(todoService.listarPorStatus(concluido));
        }
        return ResponseEntity.ok(todoService.listarTodos());
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
