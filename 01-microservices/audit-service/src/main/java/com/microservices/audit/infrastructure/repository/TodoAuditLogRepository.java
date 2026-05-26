package com.microservices.audit.infrastructure.repository;

import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoAuditLogRepository
        extends JpaRepository<TodoAuditLog, String>, TodoAuditLogRepositoryCustom {
}
