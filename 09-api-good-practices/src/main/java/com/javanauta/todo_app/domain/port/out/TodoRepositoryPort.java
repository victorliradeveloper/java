package com.javanauta.todo_app.domain.port.out;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TodoRepositoryPort {

    Todo save(Todo todo);

    Optional<Todo> findById(Long id);

    Page<Todo> findAll(TodoFilter filter, User user, Pageable pageable);

    List<Todo> findWithCursor(User user, Long cursor, Pageable pageable);

    void delete(Todo todo);
}
