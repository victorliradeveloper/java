package com.javanauta.todo_app.todo.application;

import com.javanauta.todo_app.todo.domain.exception.CompletedTodoCannotBeModifiedException;
import com.javanauta.todo_app.todo.domain.exception.DuplicateTodoException;
import com.javanauta.todo_app.todo.domain.exception.InvalidCursorException;
import com.javanauta.todo_app.todo.domain.exception.PastDueDateException;
import com.javanauta.todo_app.todo.domain.exception.TodoLimitExceededException;
import com.javanauta.todo_app.todo.domain.exception.TodoNotFoundException;
import com.javanauta.todo_app.todo.domain.model.Todo;
import com.javanauta.todo_app.todo.domain.model.TodoFilter;
import com.javanauta.todo_app.todo.domain.model.TodoPage;
import com.javanauta.todo_app.auth.domain.model.User;
import com.javanauta.todo_app.todo.domain.port.in.TodoUseCase;
import com.javanauta.todo_app.todo.domain.port.out.TodoRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TodoService implements TodoUseCase {

    private final TodoRepositoryPort todoRepository;
    private final long maxTodosPerUser;

    public TodoService(TodoRepositoryPort todoRepository,
                       @Value("${app.todo.max-per-user:100}") long maxTodosPerUser) {
        this.todoRepository = todoRepository;
        this.maxTodosPerUser = maxTodosPerUser;
    }

    @Transactional
    @Override
    public Todo create(User user, Todo todo) {
        validateDueDate(todo.getDueDate());
        if (todoRepository.existsActiveByUserAndTitle(user, todo.getTitle())) {
            throw new DuplicateTodoException(todo.getTitle());
        }

        if (todoRepository.countByUser(user) >= maxTodosPerUser) {
            throw new TodoLimitExceededException(maxTodosPerUser);
        }
        todo.setUser(user);
        try {
            // saveAndFlush forces the INSERT (and the unique-index check) to run here,
            // so a concurrent duplicate surfaces as DataIntegrityViolationException inside
            // this try instead of at commit time. The check above stays as a fast path.
            return todoRepository.saveAndFlush(todo);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTodoException(todo.getTitle());
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Todo> findAll(User user, TodoFilter filter, Pageable pageable) {
        return todoRepository.findAll(filter, user, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public TodoPage listWithCursor(User user, Long cursor, int size) {
        if (cursor != null && cursor < 0) {
            throw new InvalidCursorException(cursor);
        }
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
        if (todo.isCompleted()) {
            throw new CompletedTodoCannotBeModifiedException(id);
        }

        validateDueDate(updates.getDueDate());
        todo.setTitle(updates.getTitle());
        todo.setDescription(updates.getDescription());
        todo.setDueDate(updates.getDueDate());
        try {
            // Rely on the unique index as the source of truth for duplicates: it
            // correctly ignores this same row (only a collision with ANOTHER active
            // todo throws), unlike a pre-check on the title which would always match
            // the record being updated.
            return todoRepository.saveAndFlush(todo);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTodoException(updates.getTitle());
        }
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

    private void validateDueDate(LocalDateTime dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now())) {
            throw new PastDueDateException(dueDate);
        }
    }
}
