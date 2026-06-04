package com.javanauta.todo_app.todo.domain.port.in;

import com.javanauta.todo_app.todo.domain.model.Todo;
import com.javanauta.todo_app.todo.domain.model.TodoFilter;
import com.javanauta.todo_app.todo.domain.model.TodoPage;
import com.javanauta.todo_app.auth.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TodoUseCase {

    Todo create(User user, Todo todo);

    Page<Todo> findAll(User user, TodoFilter filter, Pageable pageable);

    TodoPage listWithCursor(User user, Long cursor, int size);

    Todo getById(User user, Long id);

    Todo update(User user, Long id, Todo updates);

    Todo complete(User user, Long id);

    void delete(User user, Long id);
}
