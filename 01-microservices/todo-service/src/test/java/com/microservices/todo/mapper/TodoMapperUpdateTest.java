package com.microservices.todo.mapper;

import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.infrastructure.entity.Todo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoMapperUpdateTest {

    private final TodoMapper mapper = Mappers.getMapper(TodoMapper.class);

    private Todo existing;
    private LocalDateTime createdAt;

    @BeforeEach
    void setUp() {
        createdAt = LocalDateTime.of(2026, 3, 14, 9, 27, 18);
        existing = Todo.builder()
                .id("7b4a9e2c-3d8f-4c1a-b6e0-9f5d2a1c8e3b")
                .title("Review pull request #142")
                .description("Check the outbox retry logic before merging")
                .completed(false)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("applies all fields when all are provided")
    void shouldApplyAllFieldsWhenProvided() {
        TodoUpdateDTO dto = new TodoUpdateDTO(
                "Finish quarterly report",
                "Numbers from finance, draft sent to Marina",
                true
        );

        mapper.updateEntity(dto, existing);

        assertEquals("Finish quarterly report", existing.getTitle());
        assertEquals("Numbers from finance, draft sent to Marina", existing.getDescription());
        assertTrue(existing.isCompleted());
    }

    @Test
    @DisplayName("ignores null fields and preserves current values (partial update)")
    void shouldIgnoreNullFieldsAndPreserveCurrent() {
        TodoUpdateDTO dto = new TodoUpdateDTO("Review pull request #142 (urgent)", null, null);

        mapper.updateEntity(dto, existing);

        assertEquals("Review pull request #142 (urgent)", existing.getTitle());
        assertEquals("Check the outbox retry logic before merging", existing.getDescription());
        assertEquals(false, existing.isCompleted());
    }

    @Test
    @DisplayName("never overrides id or createdAt even on valid updates")
    void shouldNotChangeIdOrCreatedAt() {
        TodoUpdateDTO dto = new TodoUpdateDTO("Schedule dentist appointment", "next Friday morning", true);

        mapper.updateEntity(dto, existing);

        assertEquals("7b4a9e2c-3d8f-4c1a-b6e0-9f5d2a1c8e3b", existing.getId());
        assertEquals(createdAt, existing.getCreatedAt());
    }

    @Test
    @DisplayName("fully null DTO does not change anything")
    void fullyNullDtoShouldNotChangeAnything() {
        TodoUpdateDTO dto = new TodoUpdateDTO(null, null, null);

        mapper.updateEntity(dto, existing);

        assertEquals("Review pull request #142", existing.getTitle());
        assertEquals("Check the outbox retry logic before merging", existing.getDescription());
        assertEquals(false, existing.isCompleted());
    }
}
