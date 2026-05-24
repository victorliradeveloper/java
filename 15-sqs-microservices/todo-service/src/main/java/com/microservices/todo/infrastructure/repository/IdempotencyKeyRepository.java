package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.IdempotencyKey;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyKeyRepository extends MongoRepository<IdempotencyKey, String> {
}
