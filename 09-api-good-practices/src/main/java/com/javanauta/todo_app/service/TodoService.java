package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public PagedResponseDTO<TodoResponseDTO> listarPaginado(Pageable pageable) {
        return toPagedResponse(todoRepository.findAll(pageable));
    }

    public PagedResponseDTO<TodoResponseDTO> listarPorStatusPaginado(boolean concluido, Pageable pageable) {
        return toPagedResponse(todoRepository.findByConcluido(concluido, pageable));
    }

    public CursorPageResponseDTO<TodoResponseDTO> listarComCursor(Long cursor, int size) {
        List<Todo> todos = todoRepository.findWithCursor(cursor, PageRequest.of(0, size + 1));
        boolean hasNext = todos.size() > size;
        List<Todo> content = hasNext ? todos.subList(0, size) : todos;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;
        return CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(content.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
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

    private PagedResponseDTO<TodoResponseDTO> toPagedResponse(Page<Todo> page) {
        return PagedResponseDTO.<TodoResponseDTO>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
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
