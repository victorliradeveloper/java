package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoResponseDTO criar(TodoRequestDTO request) {
        Todo todo = Todo.builder()
                .titulo(request.getTitulo())
                .descricao(request.getDescricao())
                .dataLimite(request.getDataLimite())
                .build();
        return toResponse(todoRepository.save(todo));
    }

    public List<TodoResponseDTO> listarTodos() {
        return todoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TodoResponseDTO> listarPorStatus(boolean concluido) {
        return todoRepository.findByConcluido(concluido).stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoResponseDTO buscarPorId(Long id) {
        return toResponse(buscarEntidade(id));
    }

    public TodoResponseDTO atualizar(Long id, TodoRequestDTO request) {
        Todo todo = buscarEntidade(id);
        todo.setTitulo(request.getTitulo());
        todo.setDescricao(request.getDescricao());
        todo.setDataLimite(request.getDataLimite());
        return toResponse(todoRepository.save(todo));
    }

    public TodoResponseDTO concluir(Long id) {
        Todo todo = buscarEntidade(id);
        todo.setConcluido(true);
        return toResponse(todoRepository.save(todo));
    }

    public void deletar(Long id) {
        buscarEntidade(id);
        todoRepository.deleteById(id);
    }

    private Todo buscarEntidade(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    private TodoResponseDTO toResponse(Todo todo) {
        return TodoResponseDTO.builder()
                .id(todo.getId())
                .titulo(todo.getTitulo())
                .descricao(todo.getDescricao())
                .concluido(todo.isConcluido())
                .dataCriacao(todo.getDataCriacao())
                .dataLimite(todo.getDataLimite())
                .build();
    }
}
