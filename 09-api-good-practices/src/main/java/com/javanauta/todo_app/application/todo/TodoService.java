package com.javanauta.todo_app.application.todo;

import com.javanauta.todo_app.domain.exception.TodoNotFoundException;
import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.in.TodoUseCase;
import com.javanauta.todo_app.domain.port.out.TodoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TodoService implements TodoUseCase {

    private final TodoRepositoryPort todoRepository;

    @Transactional
    @Override
    public Todo create(User user, Todo todo) {
        todo.setUser(user);
        return todoRepository.save(todo);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Todo> findAll(User user, TodoFilter filter, Pageable pageable) {
        return todoRepository.findAll(filter, user, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public TodoPage listWithCursor(User user, Long cursor, int size) {
        List<Todo> todos = todoRepository.findWithCursor(user, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = todos.size() > size;
        List<Todo> content = hasNext ? todos.subList(0, size) : todos;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;
        return new TodoPage(content, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "todos", key = "#user.id + ':' + #id")
    @Override
    public Todo getById(User user, Long id) {
        return todoRepository.findById(id)
                .filter(todo -> todo.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    @Override
    public Todo update(User user, Long id, Todo updates) {
        Todo todo = getById(user, id);
        todo.setTitle(updates.getTitle());
        todo.setDescription(updates.getDescription());
        todo.setDueDate(updates.getDueDate());
        return todoRepository.save(todo);
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    @Override
    public Todo complete(User user, Long id) {
        Todo todo = getById(user, id);
        todo.setCompleted(true);
        return todoRepository.save(todo);
    }

    @Transactional
    @CacheEvict(cacheNames = "todos", key = "#user.id + ':' + #id")
    @Override
    public void delete(User user, Long id) {
        todoRepository.delete(getById(user, id));
    }
}
