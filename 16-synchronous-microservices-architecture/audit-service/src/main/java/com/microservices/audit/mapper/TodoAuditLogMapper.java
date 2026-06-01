package com.microservices.audit.mapper;

import com.microservices.audit.dto.TodoAuditEventDTO;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TodoAuditLogMapper {

    @Mapping(target = "eventId", source = "dto.eventId")
    @Mapping(target = "aggregateId", source = "dto.todoId")
    @Mapping(target = "eventType", source = "dto.action")
    @Mapping(target = "recordedAt", expression = "java(java.time.LocalDateTime.now())")
    TodoAuditLog toAuditLog(TodoAuditEventDTO dto);
}
