package com.javanauta.todo_app.todo.domain.port.out;

import com.javanauta.todo_app.todo.domain.model.Todo;
import com.javanauta.todo_app.todo.domain.model.TodoFilter;
import com.javanauta.todo_app.auth.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TodoRepositoryPort {

    Todo save(Todo todo);

    Todo saveAndFlush(Todo todo);

    Optional<Todo> findById(Long id);

    Page<Todo> findAll(TodoFilter filter, User user, Pageable pageable);

    List<Todo> findWithCursor(User user, Long cursor, Pageable pageable);

    long countByUser(User user);

    boolean existsActiveByUserAndTitle(User user, String title);

    void delete(Todo todo);
}
