package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.Todo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TodoRepository extends MongoRepository<Todo, String> {
}
