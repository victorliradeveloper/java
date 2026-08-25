package com.microservices.audit.mapper;

import com.microservices.audit.event.TodoEvent;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoAuditLogMapperTest {

    private final TodoAuditLogMapper mapper = Mappers.getMapper(TodoAuditLogMapper.class);

    private static final String MESSAGE_ID = "outbox-7b4a9e2c-3d8f-4c1a-b6e0-9f5d2a1c8e3b";
    private static final String TODO_ID = "todo-2af71d4b-9c33-4e8b-a5e1-3d6c8f24a9b1";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 3, 14, 9, 27, 18);

    @Test
    @DisplayName("maps every field from TodoEvent and messageId param into TodoAuditLog")
    void shouldMapAllFields() {
        TodoEvent event = new TodoEvent(TODO_ID, "Review pull request #142", "CREATED", OCCURRED_AT);

        TodoAuditLog auditLog = mapper.toAuditLog(event, MESSAGE_ID);

        assertEquals(MESSAGE_ID, auditLog.getMessageId());
        assertEquals(TODO_ID, auditLog.getAggregateId());
        assertEquals("Review pull request #142", auditLog.getTitle());
        assertEquals("CREATED", auditLog.getEventType());
        assertEquals(OCCURRED_AT, auditLog.getOccurredAt());
    }

    @Test
    @DisplayName("uses the messageId parameter as the primary key (not from event)")
    void shouldUseMessageIdParamAsPrimaryKey() {
        TodoEvent event = new TodoEvent(TODO_ID, "Finish quarterly report", "UPDATED", OCCURRED_AT);

        TodoAuditLog auditLog = mapper.toAuditLog(event, MESSAGE_ID);

        assertEquals(MESSAGE_ID, auditLog.getMessageId());
    }

    @Test
    @DisplayName("recordedAt is filled with the conversion timestamp (close to now)")
    void shouldFillRecordedAtNearNow() {
        TodoEvent event = new TodoEvent(TODO_ID, "Schedule dentist appointment", "DELETED", OCCURRED_AT);
        LocalDateTime before = LocalDateTime.now();

        TodoAuditLog auditLog = mapper.toAuditLog(event, MESSAGE_ID);

        LocalDateTime after = LocalDateTime.now();
        assertTrue(
                !auditLog.getRecordedAt().isBefore(before) && !auditLog.getRecordedAt().isAfter(after),
                "recordedAt should be between before and after timestamps"
        );
        assertTrue(
                Duration.between(auditLog.getRecordedAt(), after).abs().toSeconds() <= 1,
                "recordedAt should be within 1 second of now"
        );
    }

    @Test
    @DisplayName("occurredAt is preserved verbatim from the event (not recomputed)")
    void shouldPreserveOccurredAtVerbatim() {
        TodoEvent event = new TodoEvent(TODO_ID, "Send invoice to client", "CREATED", OCCURRED_AT);

        TodoAuditLog auditLog = mapper.toAuditLog(event, MESSAGE_ID);

        assertEquals(OCCURRED_AT, auditLog.getOccurredAt());
    }
}
