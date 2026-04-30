package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.dto.response.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.mapper.TodoMapper;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.model.User;
import com.javanauta.todo_app.repository.TodoRepository;
import com.javanauta.todo_app.specification.TodoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoMapper todoMapper;

    @Transactional
    public TodoResponseDTO create(User user, TodoRequestDTO request) {
        return todoMapper.toResponse(todoRepository.save(todoMapper.toEntity(request, user)));
    }

    @Transactional(readOnly = true)
    public PagedResponseDTO<TodoResponseDTO> findAll(User user, TodoFilterDTO filter, Pageable pageable) {
        return todoMapper.toPagedResponse(todoRepository.findAll(TodoSpecification.withFilters(filter, user), pageable));
    }

    @Transactional(readOnly = true)
    public CursorPageResponseDTO<TodoResponseDTO> listWithCursor(User user, Long cursor, int size) {
        List<Todo> todos = todoRepository.findWithCursor(user, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = todos.size() > size;
        List<Todo> content = hasNext ? todos.subList(0, size) : todos;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;
        return todoMapper.toCursorResponse(content, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "todos", key = "#user.id + ':' + #id")
    public TodoResponseDTO getById(User user, Long id) {
        return todoMapper.toResponse(findEntity(id, user));
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    public TodoResponseDTO update(User user, Long id, TodoRequestDTO request) {
        Todo todo = findEntity(id, user);
        todoMapper.updateEntity(request, todo);
        return todoMapper.toResponse(todoRepository.save(todo));
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    public TodoResponseDTO complete(User user, Long id) {
        Todo todo = findEntity(id, user);
        todo.setCompleted(true);
        return todoMapper.toResponse(todoRepository.save(todo));
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    public void delete(User user, Long id) {
        findEntity(id, user);
        todoRepository.deleteById(id);
    }

    private Todo findEntity(Long id, User user) {
        return todoRepository.findById(id)
                .filter(todo -> todo.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new TodoNotFoundException(id));
    }
}
