package com.javanauta.todo_app.infrastructure.persistence.adapter;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.out.TodoRepositoryPort;
import com.javanauta.todo_app.infrastructure.persistence.repository.TodoJpaRepository;
import com.javanauta.todo_app.infrastructure.persistence.specification.TodoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TodoRepositoryAdapter implements TodoRepositoryPort {

    private final TodoJpaRepository jpaRepository;

    @Override
    public Todo save(Todo todo) {
        return jpaRepository.save(todo);
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<Todo> findAll(TodoFilter filter, User user, Pageable pageable) {
        return jpaRepository.findAll(TodoSpecification.withFilters(filter, user), pageable);
    }

    @Override
    public List<Todo> findWithCursor(User user, Long cursor, Pageable pageable) {
        return jpaRepository.findWithCursor(user, cursor, pageable);
    }

    @Override
    public void delete(Todo todo) {
        jpaRepository.delete(todo);
    }
}
