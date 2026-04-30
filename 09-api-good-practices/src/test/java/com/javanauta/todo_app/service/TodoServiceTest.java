package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.exception.TodoNotFoundException;
import com.javanauta.todo_app.model.Todo;
import com.javanauta.todo_app.model.User;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo todo;
    private TodoRequestDTO request;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L).name("Test").email("test@email.com").password("hash")
                .build();

        todo = Todo.builder()
                .id(1L)
                .title("Study Java")
                .description("Review streams and lambdas")
                .completed(false)
                .createdAt(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(3))
                .user(mockUser)
                .build();

        request = new TodoRequestDTO("Study Java", "Review streams and lambdas", LocalDateTime.now().plusDays(3));
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_shouldReturnCreatedTodo() {
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        TodoResponseDTO response = todoService.create(mockUser, request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Study Java");
        assertThat(response.getDescription()).isEqualTo("Review streams and lambdas");
        assertThat(response.isCompleted()).isFalse();
        verify(todoRepository, times(1)).save(any(Todo.class));
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_withoutFilters_shouldReturnAllItems() {
        Pageable pageable = PageRequest.of(0, 20);
        Todo other = Todo.builder().id(2L).title("Other").completed(true)
                .createdAt(LocalDateTime.now()).user(mockUser).build();
        Page<Todo> page = new PageImpl<>(List.of(todo, other), pageable, 2);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PagedResponseDTO<TodoResponseDTO> result = todoService.findAll(mockUser, new TodoFilterDTO(), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(0);
    }

    @Test
    void findAll_withCompletedFilter_shouldReturnOnlyCompletedItems() {
        Pageable pageable = PageRequest.of(0, 20);
        Todo completed = Todo.builder().id(2L).title("Done").completed(true)
                .createdAt(LocalDateTime.now()).user(mockUser).build();
        Page<Todo> page = new PageImpl<>(List.of(completed), pageable, 1);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        TodoFilterDTO filter = new TodoFilterDTO();
        filter.setCompleted(true);

        PagedResponseDTO<TodoResponseDTO> result = todoService.findAll(mockUser, filter, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isCompleted()).isTrue();
    }

    @Test
    void findAll_withTitleFilter_shouldReturnFilteredItems() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(todo), pageable, 1);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        TodoFilterDTO filter = new TodoFilterDTO();
        filter.setTitle("Study");

        PagedResponseDTO<TodoResponseDTO> result = todoService.findAll(mockUser, filter, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).contains("Study");
    }

    @Test
    void findAll_whenEmpty_shouldReturnEmptyContent() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(), pageable, 0);
        when(todoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PagedResponseDTO<TodoResponseDTO> result = todoService.findAll(mockUser, new TodoFilterDTO(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // listWithCursor
    // -------------------------------------------------------------------------

    @Test
    void listWithCursor_withoutCursor_shouldReturnFirstPage() {
        Todo todo2 = Todo.builder().id(2L).title("Second").completed(false)
                .createdAt(LocalDateTime.now()).user(mockUser).build();
        when(todoRepository.findWithCursor(eq(mockUser), isNull(), eq(PageRequest.of(0, 21))))
                .thenReturn(List.of(todo, todo2));

        CursorPageResponseDTO<TodoResponseDTO> result = todoService.listWithCursor(mockUser, null, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    void listWithCursor_withMoreItemsThanSize_shouldReturnNextCursor() {
        List<Todo> todos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            todos.add(Todo.builder().id((long) i).title("Todo " + i).completed(false)
                    .createdAt(LocalDateTime.now()).user(mockUser).build());
        }
        when(todoRepository.findWithCursor(eq(mockUser), eq(0L), eq(PageRequest.of(0, 3))))
                .thenReturn(todos);

        CursorPageResponseDTO<TodoResponseDTO> result = todoService.listWithCursor(mockUser, 0L, 2);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // getById
    // -------------------------------------------------------------------------

    @Test
    void getById_whenExists_shouldReturnTodo() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

        TodoResponseDTO response = todoService.getById(mockUser, 1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Study Java");
    }

    @Test
    void getById_whenNotExists_shouldThrowTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.getById(mockUser, 99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_whenExists_shouldReturnUpdatedTodo() {
        TodoRequestDTO newRequest = new TodoRequestDTO("New title", "New description", null);
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        TodoResponseDTO response = todoService.update(mockUser, 1L, newRequest);

        assertThat(response).isNotNull();
        verify(todoRepository).save(todo);
    }

    @Test
    void update_whenNotExists_shouldThrowTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.update(mockUser, 99L, request))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");

        verify(todoRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // complete
    // -------------------------------------------------------------------------

    @Test
    void complete_whenExists_shouldMarkAsCompleted() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(todo)).thenAnswer(inv -> {
            Todo t = inv.getArgument(0);
            t.setCompleted(true);
            return t;
        });

        TodoResponseDTO response = todoService.complete(mockUser, 1L);

        assertThat(response.isCompleted()).isTrue();
        verify(todoRepository).save(todo);
    }

    @Test
    void complete_whenNotExists_shouldThrowTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.complete(mockUser, 99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_whenExists_shouldDeleteWithoutError() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoService.delete(mockUser, 1L);

        verify(todoRepository).deleteById(1L);
    }

    @Test
    void delete_whenNotExists_shouldThrowTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.delete(mockUser, 99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");

        verify(todoRepository, never()).deleteById(any());
    }
}
