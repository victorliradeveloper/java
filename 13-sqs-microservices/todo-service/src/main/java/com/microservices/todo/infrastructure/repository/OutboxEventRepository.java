package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutboxEventRepository
        extends MongoRepository<OutboxEvent, String>, OutboxEventRepositoryCustom {
}
