package com.javanauta.todo_app.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javanauta.todo_app.domain.exception.TodoNotFoundException;
import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.in.TodoUseCase;
import com.javanauta.todo_app.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.TodoResponseDTO;
import com.javanauta.todo_app.interfaces.mapper.TodoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TodoController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodoUseCase todoUseCase;

    @MockBean
    private TodoMapper todoMapper;

    private TodoResponseDTO response;
    private TodoRequestDTO request;
    private User mockUser;
    private Todo todo;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        mockUser = User.builder()
                .id(1L).name("Test").email("test@email.com").password("hash")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, List.of()));

        todo = Todo.builder()
                .id(1L)
                .title("Study Java")
                .description("Review streams")
                .completed(false)
                .createdAt(now)
                .dueDate(now.plusDays(3))
                .user(mockUser)
                .build();

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void create_withValidData_shouldReturn201() throws Exception {
        when(todoMapper.toEntity(any(TodoRequestDTO.class))).thenReturn(todo);
        when(todoUseCase.create(any(User.class), any(Todo.class))).thenReturn(todo);
        when(todoMapper.toResponse(any(Todo.class))).thenReturn(response);

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

        verify(todoUseCase, never()).create(any(), any());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void list_withoutFilters_shouldReturnPaginatedItems() throws Exception {
        Page<Todo> page = new PageImpl<>(List.of(todo));
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoUseCase.findAll(any(User.class), any(), any(Pageable.class))).thenReturn(page);
        when(todoMapper.toPagedResponse(any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_withTitleFilter_shouldReturnFilteredItems() throws Exception {
        Page<Todo> page = new PageImpl<>(List.of(todo));
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoUseCase.findAll(any(User.class), any(), any(Pageable.class))).thenReturn(page);
        when(todoMapper.toPagedResponse(any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("title", "Study"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"));
    }

    @Test
    void list_withCompletedFilter_shouldReturnFilteredItems() throws Exception {
        TodoResponseDTO completed = TodoResponseDTO.builder()
                .id(2L).title("Done").completed(true).createdAt(LocalDateTime.now()).build();
        Page<Todo> page = new PageImpl<>(List.of(todo));
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(completed))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoUseCase.findAll(any(User.class), any(), any(Pageable.class))).thenReturn(page);
        when(todoMapper.toPagedResponse(any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].completed").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos/cursor
    // -------------------------------------------------------------------------

    @Test
    void listWithCursor_withoutCursor_shouldReturnFirstPage() throws Exception {
        TodoPage todoPage = new TodoPage(List.of(todo), null, false);
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(null)
                .hasNext(false)
                .build();
        when(todoUseCase.listWithCursor(any(User.class), isNull(), eq(20))).thenReturn(todoPage);
        when(todoMapper.toCursorResponse(any(TodoPage.class))).thenReturn(cursorPage);

        mockMvc.perform(get("/api/v1/todos/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Study Java"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listWithCursor_withCursor_shouldReturnNextPage() throws Exception {
        TodoPage todoPage = new TodoPage(List.of(todo), 10L, true);
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(10L)
                .hasNext(true)
                .build();
        when(todoUseCase.listWithCursor(any(User.class), eq(5L), eq(20))).thenReturn(todoPage);
        when(todoMapper.toCursorResponse(any(TodoPage.class))).thenReturn(cursorPage);

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
        when(todoUseCase.getById(any(User.class), eq(1L))).thenReturn(todo);
        when(todoMapper.toResponse(any(Todo.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Study Java"));
    }

    @Test
    void getById_whenNotExists_shouldReturn404() throws Exception {
        when(todoUseCase.getById(any(User.class), eq(99L))).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(get("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void update_withValidData_shouldReturn200() throws Exception {
        when(todoMapper.toEntity(any(TodoRequestDTO.class))).thenReturn(todo);
        when(todoUseCase.update(any(User.class), eq(1L), any(Todo.class))).thenReturn(todo);
        when(todoMapper.toResponse(any(Todo.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Study Java"));
    }

    @Test
    void update_whenNotExists_shouldReturn404() throws Exception {
        when(todoMapper.toEntity(any(TodoRequestDTO.class))).thenReturn(todo);
        when(todoUseCase.update(any(User.class), eq(99L), any())).thenThrow(new TodoNotFoundException(99L));

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
        Todo completedTodo = Todo.builder()
                .id(1L).title("Study Java").completed(true).createdAt(LocalDateTime.now()).build();
        TodoResponseDTO completedResponse = TodoResponseDTO.builder()
                .id(1L).title("Study Java").completed(true).createdAt(LocalDateTime.now()).build();
        when(todoUseCase.complete(any(User.class), eq(1L))).thenReturn(completedTodo);
        when(todoMapper.toResponse(completedTodo)).thenReturn(completedResponse);

        mockMvc.perform(patch("/api/v1/todos/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void complete_whenNotExists_shouldReturn404() throws Exception {
        when(todoUseCase.complete(any(User.class), eq(99L))).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(patch("/api/v1/todos/99/complete"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void delete_whenExists_shouldReturn204() throws Exception {
        doNothing().when(todoUseCase).delete(any(User.class), eq(1L));

        mockMvc.perform(delete("/api/v1/todos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenNotExists_shouldReturn404() throws Exception {
        doThrow(new TodoNotFoundException(99L)).when(todoUseCase).delete(any(User.class), eq(99L));

        mockMvc.perform(delete("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }
}
