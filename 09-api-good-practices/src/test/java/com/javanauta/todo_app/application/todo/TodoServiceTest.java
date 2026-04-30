package com.javanauta.todo_app.application.todo;

import com.javanauta.todo_app.domain.exception.TodoNotFoundException;
import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.out.TodoRepositoryPort;
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
    private TodoRepositoryPort todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo todo;
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
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_shouldReturnCreatedTodo() {
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        Todo result = todoService.create(mockUser, todo);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Study Java");
        assertThat(result.isCompleted()).isFalse();
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
        when(todoRepository.findAll(any(TodoFilter.class), eq(mockUser), eq(pageable))).thenReturn(page);

        Page<Todo> result = todoService.findAll(mockUser, new TodoFilter(null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test
    void findAll_withCompletedFilter_shouldReturnOnlyCompletedItems() {
        Pageable pageable = PageRequest.of(0, 20);
        Todo completed = Todo.builder().id(2L).title("Done").completed(true)
                .createdAt(LocalDateTime.now()).user(mockUser).build();
        Page<Todo> page = new PageImpl<>(List.of(completed), pageable, 1);
        when(todoRepository.findAll(any(TodoFilter.class), eq(mockUser), eq(pageable))).thenReturn(page);

        Page<Todo> result = todoService.findAll(mockUser, new TodoFilter(null, true, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isCompleted()).isTrue();
    }

    @Test
    void findAll_withTitleFilter_shouldReturnFilteredItems() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(todo), pageable, 1);
        when(todoRepository.findAll(any(TodoFilter.class), eq(mockUser), eq(pageable))).thenReturn(page);

        Page<Todo> result = todoService.findAll(mockUser, new TodoFilter("Study", null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).contains("Study");
    }

    @Test
    void findAll_whenEmpty_shouldReturnEmptyContent() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Todo> page = new PageImpl<>(List.of(), pageable, 0);
        when(todoRepository.findAll(any(TodoFilter.class), eq(mockUser), eq(pageable))).thenReturn(page);

        Page<Todo> result = todoService.findAll(mockUser, new TodoFilter(null, null, null, null), pageable);

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

        TodoPage result = todoService.listWithCursor(mockUser, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
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

        TodoPage result = todoService.listWithCursor(mockUser, 0L, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // getById
    // -------------------------------------------------------------------------

    @Test
    void getById_whenExists_shouldReturnTodo() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

        Todo result = todoService.getById(mockUser, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Study Java");
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
        Todo updates = Todo.builder().title("New title").description("New description").build();
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenReturn(todo);

        Todo result = todoService.update(mockUser, 1L, updates);

        assertThat(result).isNotNull();
        verify(todoRepository).save(todo);
    }

    @Test
    void update_whenNotExists_shouldThrowTodoNotFoundException() {
        Todo updates = Todo.builder().title("New title").build();
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.update(mockUser, 99L, updates))
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

        Todo result = todoService.complete(mockUser, 1L);

        assertThat(result.isCompleted()).isTrue();
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

        verify(todoRepository).delete(todo);
    }

    @Test
    void delete_whenNotExists_shouldThrowTodoNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.delete(mockUser, 99L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("99");

        verify(todoRepository, never()).delete(any());
    }
}
