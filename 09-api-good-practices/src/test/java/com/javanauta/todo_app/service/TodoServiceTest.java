package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo todo;
    private TodoRequestDTO request;

    @BeforeEach
    void setUp() {
        todo = Todo.builder()
                .id(1L)
                .titulo("Estudar Java")
                .descricao("Revisar streams e lambdas")
                .concluido(false)
                .dataCriacao(LocalDateTime.now())
                .dataLimite(LocalDateTime.now().plusDays(3))
                .build();

        request = new TodoRequestDTO("Estudar Java", "Revisar streams e lambdas", LocalDateTime.now().plusDays(3));
    }

    // -------------------------------------------------------------------------
    // criar
    // -------------------------------------------------------------------------

    @Test
    void criar_deveRetornarTodoCriado() {
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        TodoResponseDTO response = todoService.criar(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitulo()).isEqualTo("Estudar Java");
        assertThat(response.getDescricao()).isEqualTo("Revisar streams e lambdas");
        assertThat(response.isConcluido()).isFalse();
        verify(todoRepository, times(1)).save(any(Todo.class));
    }

    // -------------------------------------------------------------------------
    // listar
    // -------------------------------------------------------------------------

    @Test
    void listar_semFiltros_deveRetornarTodosOsItens() {
        Pageable pageable = PageRequest.of(0, 20);
        Todo outro = Todo.builder().id(2L).titulo("Outro").concluido(true).dataCriacao(LocalDateTime.now()).build();
        Page<Todo> page = new PageImpl<>(List.of(todo, outro), pageable, 2);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PagedResponseDTO<TodoResponseDTO> resultado = todoService.listar(new TodoFilterDTO(), pageable);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.getTotalElements()).isEqualTo(2);
        assertThat(resultado.getPage()).isEqualTo(0);
    }

    @Test
    void listar_comFiltroConcluido_deveRetornarApenasItensConcluidos() {
        Pageable pageable = PageRequest.of(0, 20);
        Todo concluido = Todo.builder().id(2L).titulo("Feito").concluido(true).dataCriacao(LocalDateTime.now()).build();
        Page<Todo> page = new PageImpl<>(List.of(concluido), pageable, 1);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        TodoFilterDTO filtro = new TodoFilterDTO();
        filtro.setConcluido(true);

        PagedResponseDTO<TodoResponseDTO> resultado = todoService.listar(filtro, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).isConcluido()).isTrue();
    }

    @Test
    void listar_comFiltroTitulo_deveRetornarItensFiltrados() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(todo), pageable, 1);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        TodoFilterDTO filtro = new TodoFilterDTO();
        filtro.setTitulo("Estudar");

        PagedResponseDTO<TodoResponseDTO> resultado = todoService.listar(filtro, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getTitulo()).contains("Estudar");
    }

    @Test
    void listar_quandoVazio_deveRetornarConteudoVazio() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(), pageable, 0);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PagedResponseDTO<TodoResponseDTO> resultado = todoService.listar(new TodoFilterDTO(), pageable);

        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getTotalElements()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // listarComCursor
    // -------------------------------------------------------------------------

    @Test
    void listarComCursor_semCursor_deveRetornarPrimeiraPagina() {
        Todo todo2 = Todo.builder().id(2L).titulo("Segundo").concluido(false).dataCriacao(LocalDateTime.now()).build();
        when(todoRepository.findWithCursor(null, PageRequest.of(0, 21))).thenReturn(List.of(todo, todo2));

        CursorPageResponseDTO<TodoResponseDTO> resultado = todoService.listarComCursor(null, 20);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.isHasNext()).isFalse();
        assertThat(resultado.getNextCursor()).isNull();
    }

    @Test
    void listarComCursor_comMaisItensQueSize_deveRetornarNextCursor() {
        List<Todo> todos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            todos.add(Todo.builder().id((long) i).titulo("Todo " + i).concluido(false).dataCriacao(LocalDateTime.now()).build());
        }
        when(todoRepository.findWithCursor(0L, PageRequest.of(0, 3))).thenReturn(todos);

        CursorPageResponseDTO<TodoResponseDTO> resultado = todoService.listarComCursor(0L, 2);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.isHasNext()).isTrue();
        assertThat(resultado.getNextCursor()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // buscarPorId
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExiste_deveRetornarTodo() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

        TodoResponseDTO response = todoService.buscarPorId(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitulo()).isEqualTo("Estudar Java");
    }

    @Test
    void buscarPorId_quandoNaoExiste_deveLancarTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.buscarPorId(99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // atualizar
    // -------------------------------------------------------------------------

    @Test
    void atualizar_quandoExiste_deveRetornarTodoAtualizado() {
        TodoRequestDTO novoRequest = new TodoRequestDTO("Novo título", "Nova descrição", null);
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        TodoResponseDTO response = todoService.atualizar(1L, novoRequest);

        assertThat(response).isNotNull();
        verify(todoRepository).save(todo);
    }

    @Test
    void atualizar_quandoNaoExiste_deveLancarTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.atualizar(99L, request))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");

        verify(todoRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // concluir
    // -------------------------------------------------------------------------

    @Test
    void concluir_quandoExiste_deveMarcarlComoConcluido() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(todo)).thenAnswer(inv -> {
            Todo t = inv.getArgument(0);
            t.setConcluido(true);
            return t;
        });

        TodoResponseDTO response = todoService.concluir(1L);

        assertThat(response.isConcluido()).isTrue();
        verify(todoRepository).save(todo);
    }

    @Test
    void concluir_quandoNaoExiste_deveLancarTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.concluir(99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // deletar
    // -------------------------------------------------------------------------

    @Test
    void deletar_quandoExiste_deveDeletarSemErro() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoService.deletar(1L);

        verify(todoRepository).deleteById(1L);
    }

    @Test
    void deletar_quandoNaoExiste_deveLancarTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.deletar(99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");

        verify(todoRepository, never()).deleteById(any());
    }
}
