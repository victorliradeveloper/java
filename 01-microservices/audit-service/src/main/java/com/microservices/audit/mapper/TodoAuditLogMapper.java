package com.microservices.audit.mapper;

import com.microservices.audit.event.TodoEvent;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TodoAuditLogMapper {

    @Mapping(target = "aggregateId", source = "event.todoId")
    @Mapping(target = "eventType", source = "event.action")
    @Mapping(target = "recordedAt", expression = "java(java.time.LocalDateTime.now())")
    TodoAuditLog toAuditLog(TodoEvent event, String messageId);
}
