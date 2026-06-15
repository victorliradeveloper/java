package com.microservices.todo.controller;

import com.microservices.todo.dto.request.TodoReplaceDTO;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.idempotency.IdempotencyService;
import com.microservices.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoController {

    // Fingerprint identifica a operacao no hash da idempotencia: previne que
    // a mesma key seja reusada acidentalmente em endpoints diferentes.
    private static final String CREATE_FINGERPRINT = "POST /todos";

    private final TodoService service;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid TodoRequestDTO dto) {
        TodoResponseDTO body = idempotencyService.executeIdempotent(
                idempotencyKey,
                CREATE_FINGERPRINT,
                dto,
                TodoResponseDTO.class,
                () -> service.create(dto)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public ResponseEntity<List<TodoResponseDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    // PUT = substituicao total: body representa o recurso INTEIRO. title e
    // obrigatorio (@NotBlank no TodoReplaceDTO + @Valid); campos omitidos resetam.
    @PutMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> update(@PathVariable String id, @RequestBody @Valid TodoReplaceDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    // PATCH = atualizacao parcial: aplica so os campos enviados (null e ignorado).
    // PUT e PATCH compartilham a orquestracao (TodoService.applyChange) e diferem
    // apenas na estrategia de merge. Detalhes em docs/http-methods/patch.md.
    @PatchMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> patch(@PathVariable String id, @RequestBody TodoUpdateDTO dto) {
        return ResponseEntity.ok(service.patch(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
