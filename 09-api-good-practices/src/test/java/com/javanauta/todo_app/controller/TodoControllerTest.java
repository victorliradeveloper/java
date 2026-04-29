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
        LocalDateTime agora = LocalDateTime.now();

        response = TodoResponseDTO.builder()
                .id(1L)
                .titulo("Estudar Java")
                .descricao("Revisar streams")
                .concluido(false)
                .dataCriacao(agora)
                .dataLimite(agora.plusDays(3))
                .build();

        request = new TodoRequestDTO("Estudar Java", "Revisar streams", agora.plusDays(3));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void criar_comDadosValidos_deveRetornar201() throws Exception {
        when(todoService.criar(any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Estudar Java"))
                .andExpect(jsonPath("$.concluido").value(false));
    }

    @Test
    void criar_semTitulo_deveRetornar400() throws Exception {
        TodoRequestDTO requestInvalido = new TodoRequestDTO("", "descrição", null);

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).criar(any());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos
    // -------------------------------------------------------------------------

    @Test
    void listar_semFiltros_deveRetornarItensPaginados() throws Exception {
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.listar(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Estudar Java"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listar_comFiltroTitulo_deveRetornarItensFiltrados() throws Exception {
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.listar(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("titulo", "Estudar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Estudar Java"));
    }

    @Test
    void listar_comFiltroConcluido_deveRetornarItensFiltrados() throws Exception {
        TodoResponseDTO concluido = TodoResponseDTO.builder()
                .id(2L).titulo("Feito").concluido(true).dataCriacao(LocalDateTime.now()).build();
        PagedResponseDTO<TodoResponseDTO> paged = PagedResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(concluido))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(todoService.listar(any(TodoFilterDTO.class), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/todos").param("concluido", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].concluido").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos/cursor
    // -------------------------------------------------------------------------

    @Test
    void listarComCursor_semCursor_deveRetornarPrimeiraPagina() throws Exception {
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(null)
                .hasNext(false)
                .build();
        when(todoService.listarComCursor(isNull(), eq(20))).thenReturn(cursorPage);

        mockMvc.perform(get("/api/v1/todos/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Estudar Java"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listarComCursor_comCursor_deveRetornarProximaPagina() throws Exception {
        CursorPageResponseDTO<TodoResponseDTO> cursorPage = CursorPageResponseDTO.<TodoResponseDTO>builder()
                .content(List.of(response))
                .nextCursor(10L)
                .hasNext(true)
                .build();
        when(todoService.listarComCursor(eq(5L), eq(20))).thenReturn(cursorPage);

        mockMvc.perform(get("/api/v1/todos/cursor").param("cursor", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value(10))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExiste_deveRetornar200() throws Exception {
        when(todoService.buscarPorId(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Estudar Java"));
    }

    @Test
    void buscarPorId_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.buscarPorId(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(get("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void atualizar_comDadosValidos_deveRetornar200() throws Exception {
        when(todoService.atualizar(eq(1L), any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Estudar Java"));
    }

    @Test
    void atualizar_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.atualizar(eq(99L), any())).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(put("/api/v1/todos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/todos/{id}/concluir
    // -------------------------------------------------------------------------

    @Test
    void concluir_quandoExiste_deveRetornar200() throws Exception {
        TodoResponseDTO concluido = TodoResponseDTO.builder()
                .id(1L).titulo("Estudar Java").concluido(true).dataCriacao(LocalDateTime.now()).build();
        when(todoService.concluir(1L)).thenReturn(concluido);

        mockMvc.perform(patch("/api/v1/todos/1/concluir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concluido").value(true));
    }

    @Test
    void concluir_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.concluir(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(patch("/api/v1/todos/99/concluir"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/todos/{id}
    // -------------------------------------------------------------------------

    @Test
    void deletar_quandoExiste_deveRetornar204() throws Exception {
        doNothing().when(todoService).deletar(1L);

        mockMvc.perform(delete("/api/v1/todos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletar_quandoNaoExiste_deveRetornar404() throws Exception {
        doThrow(new TodoNotFoundException(99L)).when(todoService).deletar(99L);

        mockMvc.perform(delete("/api/v1/todos/99"))
                .andExpect(status().isNotFound());
    }
}
