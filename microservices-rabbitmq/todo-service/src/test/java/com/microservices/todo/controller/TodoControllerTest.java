package com.microservices.todo.controller;

import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.idempotency.IdempotencyService;
import com.microservices.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoControllerTest {

    @Mock
    private TodoService service;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private TodoController controller;

    private TodoResponseDTO sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new TodoResponseDTO(
                "7b4a9e2c-3d8f-4c1a-b6e0-9f5d2a1c8e3b",
                "Buy bread",
                "from the bakery",
                false,
                LocalDateTime.of(2026, 3, 14, 9, 27, 18)
        );
    }

    @Test
    @DisplayName("create returns 201 CREATED and delegates to idempotencyService with fingerprint")
    void shouldCreateThroughIdempotencyService() {
        TodoRequestDTO request = new TodoRequestDTO("Buy bread", "from the bakery");
        String idempotencyKey = "client-key-123";

        when(idempotencyService.executeIdempotent(
                eq(idempotencyKey),
                eq("POST /todos"),
                eq(request),
                eq(TodoResponseDTO.class),
                any()
        )).thenReturn(sampleResponse);

        ResponseEntity<TodoResponseDTO> response = controller.create(idempotencyKey, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(sampleResponse, response.getBody());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("create works without Idempotency-Key header (null key)")
    void shouldCreateWithoutIdempotencyKey() {
        TodoRequestDTO request = new TodoRequestDTO("Buy bread", null);

        when(idempotencyService.executeIdempotent(
                eq(null),
                eq("POST /todos"),
                eq(request),
                eq(TodoResponseDTO.class),
                any()
        )).thenReturn(sampleResponse);

        ResponseEntity<TodoResponseDTO> response = controller.create(null, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(sampleResponse, response.getBody());
    }

    @Test
    @DisplayName("create supplier invokes service.create with the request DTO")
    @SuppressWarnings("unchecked")
    void supplierShouldDelegateToServiceCreate() {
        TodoRequestDTO request = new TodoRequestDTO("Buy bread", "from the bakery");
        when(service.create(request)).thenReturn(sampleResponse);

        ArgumentCaptor<Supplier<TodoResponseDTO>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        when(idempotencyService.executeIdempotent(
                eq("k1"),
                eq("POST /todos"),
                eq(request),
                eq(TodoResponseDTO.class),
                supplierCaptor.capture()
        )).thenAnswer(invocation -> supplierCaptor.getValue().get());

        ResponseEntity<TodoResponseDTO> response = controller.create("k1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(sampleResponse, response.getBody());
        verify(service, times(1)).create(request);
    }

    @Test
    @DisplayName("findAll returns 200 OK with list from service")
    void shouldReturnAllTodos() {
        List<TodoResponseDTO> todos = List.of(sampleResponse);
        when(service.findAll()).thenReturn(todos);

        ResponseEntity<List<TodoResponseDTO>> response = controller.findAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(todos, response.getBody());
    }

    @Test
    @DisplayName("findAll returns 200 OK with empty list when there are no todos")
    void shouldReturnEmptyListWhenNoTodos() {
        when(service.findAll()).thenReturn(List.of());

        ResponseEntity<List<TodoResponseDTO>> response = controller.findAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

    @Test
    @DisplayName("findById returns 200 OK with the requested todo")
    void shouldReturnTodoById() {
        String id = sampleResponse.id();
        when(service.findById(id)).thenReturn(sampleResponse);

        ResponseEntity<TodoResponseDTO> response = controller.findById(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(sampleResponse, response.getBody());
        verify(service).findById(id);
    }

    @Test
    @DisplayName("update returns 200 OK and forwards id and DTO to service")
    void shouldUpdateTodo() {
        String id = sampleResponse.id();
        TodoUpdateDTO dto = new TodoUpdateDTO("Finish quarterly report", null, true);
        when(service.update(id, dto)).thenReturn(sampleResponse);

        ResponseEntity<TodoResponseDTO> response = controller.update(id, dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(sampleResponse, response.getBody());
        verify(service).update(id, dto);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    @DisplayName("delete returns 204 No Content with empty body")
    void shouldDeleteTodo() {
        String id = sampleResponse.id();

        ResponseEntity<Void> response = controller.delete(id);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(service, times(1)).delete(id);
        verify(service, never()).findById(any());
        verifyNoInteractions(idempotencyService);
    }
}
