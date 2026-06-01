package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, String> {
}
