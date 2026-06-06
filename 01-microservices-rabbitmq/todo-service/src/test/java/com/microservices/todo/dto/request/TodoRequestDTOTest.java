package com.microservices.todo.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoRequestDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("accepts DTO with a non-blank title")
    void shouldAcceptNonBlankTitle() {
        TodoRequestDTO dto = new TodoRequestDTO("buy bread", "from the bakery");

        Set<ConstraintViolation<TodoRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "valid DTO should not produce violations");
    }

    @Test
    @DisplayName("rejects null title")
    void shouldRejectNullTitle() {
        TodoRequestDTO dto = new TodoRequestDTO(null, "anything");

        Set<ConstraintViolation<TodoRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("rejects empty title")
    void shouldRejectEmptyTitle() {
        TodoRequestDTO dto = new TodoRequestDTO("", "anything");

        Set<ConstraintViolation<TodoRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("rejects whitespace-only title")
    void shouldRejectWhitespaceOnlyTitle() {
        TodoRequestDTO dto = new TodoRequestDTO("   ", "anything");

        Set<ConstraintViolation<TodoRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("title", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("accepts null description (optional field)")
    void shouldAcceptNullDescription() {
        TodoRequestDTO dto = new TodoRequestDTO("valid title", null);

        Set<ConstraintViolation<TodoRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }
}
