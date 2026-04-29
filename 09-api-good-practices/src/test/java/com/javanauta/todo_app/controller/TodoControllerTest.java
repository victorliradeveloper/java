package com.javanauta.todo_app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodoService todoService;

    private TodoResponseDTO response;
    private TodoRequestDTO request;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        response = TodoResponseDTO.builder()
                .id(1L)
                .title("Study Java")
                .description("Review streams")
                .completed(false)
                .createdAt(now)
                .dueDate(now.plusDays(3))
                .build();

        request = new TodoRequestDTO("Study Java", "Review streams", now.plusDays(3));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void create_withValidData_shouldReturn201() throws Exception {
        when(todoService.create(any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Study Java"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void create_withoutTitle_shouldReturn400() throws Exception {
        TodoRequestDTO invalidRequest = new TodoRequestDTO("", "description", null);

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).create(any());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void list_withoutFilters_shouldReturnPaginatedItems() throws Exception {
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.findAll(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_withTitleFilter_shouldReturnFilteredItems() throws Exception {
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.findAll(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("title", "Study"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"));
    }

    @Test
    void list_withCompletedFilter_shouldReturnFilteredItems() throws Exception {
        TodoResponseDTO completed = TodoResponseDTO.builder()
                .id(2L).title("Done").completed(true).createdAt(LocalDateTime.now()).build();
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(completed))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.findAll(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].completed").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos/cursor
    // -------------------------------------------------------------------------

    @Test
    void listWithCursor_withoutCursor_shouldReturnFirstPage() throws Exception {
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(null)
                .hasNext(false)
                .build();
        when(todoService.listWithCursor(isNull(), eq(20))).thenReturn(cursorPage);

        mockMvc.perform(get("/api/v1/todos/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listWithCursor_withCursor_shouldReturnNextPage() throws Exception {
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(10L)
                .hasNext(true)
                .build();
        when(todoService.listWithCursor(eq(5L), eq(20))).thenReturn(cursorPage);

        mockMvc.perform(get("/api/v1/todos/cursor").param("cursor", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value(10))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void getById_whenExists_shouldReturn200() throws Exception {
        when(todoService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Study Java"));
    }

    @Test
    void getById_whenNotExists_shouldReturn404() throws Exception {
        when(todoService.getById(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(get("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void update_withValidData_shouldReturn200() throws Exception {
        when(todoService.update(eq(1L), any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Study Java"));
    }

    @Test
    void update_whenNotExists_shouldReturn404() throws Exception {
        when(todoService.update(eq(99L), any())).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(put("/api/v1/todos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/todos/{id}/complete
    // -------------------------------------------------------------------------

    @Test
    void complete_whenExists_shouldReturn200() throws Exception {
        TodoResponseDTO completed = TodoResponseDTO.builder()
                .id(1L).title("Study Java").completed(true).createdAt(LocalDateTime.now()).build();
        when(todoService.complete(1L)).thenReturn(completed);

        mockMvc.perform(patch("/api/v1/todos/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void complete_whenNotExists_shouldReturn404() throws Exception {
        when(todoService.complete(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(patch("/api/v1/todos/99/complete"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void delete_whenExists_shouldReturn204() throws Exception {
        doNothing().when(todoService).delete(1L);

        mockMvc.perform(delete("/api/v1/todos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenNotExists_shouldReturn404() throws Exception {
        doThrow(new TodoNotFoundException(99L)).when(todoService).delete(99L);

        mockMvc.perform(delete("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }
}
