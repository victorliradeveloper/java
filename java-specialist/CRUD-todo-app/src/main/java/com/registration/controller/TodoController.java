package com.registration.controller;

import com.registration.dto.request.TodoRequestDTO;
import com.registration.dto.request.TodoUpdateDTO;
import com.registration.dto.response.TodoResponseDTO;
import com.registration.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoController {

    //Eu não preciso injetar a dependência manualmente ou via @Autowired, pois o Lombok faz isso
    private final TodoService todoService;

    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<TodoResponseDTO>> findAll() {
        return ResponseEntity.ok(todoService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(todoService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> update(@PathVariable String id, @RequestBody TodoUpdateDTO dto) {
        return ResponseEntity.ok(todoService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
