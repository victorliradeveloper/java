package com.javanauta.todo_app.service;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    // listarTodos
    // -------------------------------------------------------------------------

    @Test
    void listarTodos_deveRetornarListaComTodosOsItens() {
        Todo outro = Todo.builder().id(2L).titulo("Outro").concluido(true).dataCriacao(LocalDateTime.now()).build();
        when(todoRepository.findAll()).thenReturn(List.of(todo, outro));

        List<TodoResponseDTO> resultado = todoService.listarTodos();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getId()).isEqualTo(1L);
        assertThat(resultado.get(1).getId()).isEqualTo(2L);
    }

    @Test
    void listarTodos_quandoVazio_deveRetornarListaVazia() {
        when(todoRepository.findAll()).thenReturn(List.of());

        List<TodoResponseDTO> resultado = todoService.listarTodos();

        assertThat(resultado).isEmpty();
    }

    // -------------------------------------------------------------------------
    // listarPorStatus
    // -------------------------------------------------------------------------

    @Test
    void listarPorStatus_deveRetornarApenasItensConcluidos() {
        Todo concluido = Todo.builder().id(2L).titulo("Feito").concluido(true).dataCriacao(LocalDateTime.now()).build();
        when(todoRepository.findByConcluido(true)).thenReturn(List.of(concluido));

        List<TodoResponseDTO> resultado = todoService.listarPorStatus(true);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).isConcluido()).isTrue();
    }

    @Test
    void listarPorStatus_deveRetornarApenasItensPendentes() {
        when(todoRepository.findByConcluido(false)).thenReturn(List.of(todo));

        List<TodoResponseDTO> resultado = todoService.listarPorStatus(false);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).isConcluido()).isFalse();
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
