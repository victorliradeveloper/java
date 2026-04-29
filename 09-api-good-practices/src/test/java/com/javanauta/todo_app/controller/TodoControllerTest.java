package com.javanauta.todo_app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    // POST /todo
    // -------------------------------------------------------------------------

    @Test
    void criar_comDadosValidos_deveRetornar201() throws Exception {
        when(todoService.criar(any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/todo")
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

        mockMvc.perform(post("/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).criar(any());
    }

    // -------------------------------------------------------------------------
    // GET /todo
    // -------------------------------------------------------------------------

    @Test
    void listar_semFiltro_deveRetornarTodosOsItens() throws Exception {
        when(todoService.listarTodos()).thenReturn(List.of(response));

        mockMvc.perform(get("/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Estudar Java"));
    }

    @Test
    void listar_comFiltroConcluido_deveRetornarItensFiltrados() throws Exception {
        TodoResponseDTO concluido = TodoResponseDTO.builder()
                .id(2L).titulo("Feito").concluido(true).dataCriacao(LocalDateTime.now()).build();
        when(todoService.listarPorStatus(true)).thenReturn(List.of(concluido));

        mockMvc.perform(get("/todo").param("concluido", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].concluido").value(true));
    }

    @Test
    void listar_comFiltroPendente_deveRetornarItensPendentes() throws Exception {
        when(todoService.listarPorStatus(false)).thenReturn(List.of(response));

        mockMvc.perform(get("/todo").param("concluido", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].concluido").value(false));
    }

    // -------------------------------------------------------------------------
    // GET /todo/{id}
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExiste_deveRetornar200() throws Exception {
        when(todoService.buscarPorId(1L)).thenReturn(response);

        mockMvc.perform(get("/todo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.titulo").value("Estudar Java"));
    }

    @Test
    void buscarPorId_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.buscarPorId(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(get("/todo/99"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PUT /todo/{id}
    // -------------------------------------------------------------------------

    @Test
    void atualizar_comDadosValidos_deveRetornar200() throws Exception {
        when(todoService.atualizar(eq(1L), any(TodoRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/todo/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Estudar Java"));
    }

    @Test
    void atualizar_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.atualizar(eq(99L), any())).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(put("/todo/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PATCH /todo/{id}/concluir
    // -------------------------------------------------------------------------

    @Test
    void concluir_quandoExiste_deveRetornar200() throws Exception {
        TodoResponseDTO concluido = TodoResponseDTO.builder()
                .id(1L).titulo("Estudar Java").concluido(true).dataCriacao(LocalDateTime.now()).build();
        when(todoService.concluir(1L)).thenReturn(concluido);

        mockMvc.perform(patch("/todo/1/concluir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concluido").value(true));
    }

    @Test
    void concluir_quandoNaoExiste_deveRetornar404() throws Exception {
        when(todoService.concluir(99L)).thenThrow(new TodoNotFoundException(99L));

        mockMvc.perform(patch("/todo/99/concluir"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /todo/{id}
    // -------------------------------------------------------------------------

    @Test
    void deletar_quandoExiste_deveRetornar204() throws Exception {
        doNothing().when(todoService).deletar(1L);

        mockMvc.perform(delete("/todo/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletar_quandoNaoExiste_deveRetornar404() throws Exception {
        doThrow(new TodoNotFoundException(99L)).when(todoService).deletar(99L);

        mockMvc.perform(delete("/todo/99"))
                .andExpect(status().isNotFound());
    }
}
