package com.registration.infrastructure.repository;

import com.registration.infrastructure.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, String> {
}
