package com.microservices.audit.infrastructure.repository;

import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TodoAuditLogRepository extends MongoRepository<TodoAuditLog, String> {
}
