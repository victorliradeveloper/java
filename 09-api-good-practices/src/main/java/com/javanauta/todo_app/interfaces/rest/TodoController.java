package com.javanauta.todo_app.interfaces.rest;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.TodoPage;
import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.in.TodoUseCase;
import com.javanauta.todo_app.interfaces.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.CursorPageResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.ErrorResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.PagedResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.TodoResponseDTO;
import com.javanauta.todo_app.interfaces.mapper.TodoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Todos", description = "Todo management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoUseCase todoUseCase;
    private final TodoMapper todoMapper;

    @Operation(summary = "Create a new todo")
    @ApiResponse(responseCode = "201", description = "Todo created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        Todo saved = todoUseCase.create(getAuthenticatedUser(), todoMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(todoMapper.toResponse(saved));
    }

    @Operation(summary = "List todos with pagination and filters")
    @ApiResponse(responseCode = "200", description = "Paginated list of todos")
    @Parameter(name = "title", description = "Filter by title (partial match)")
    @Parameter(name = "completed", description = "Filter by completion status")
    @Parameter(name = "dueDateFrom", description = "Filter todos due after this date (ISO 8601)")
    @Parameter(name = "dueDateTo", description = "Filter todos due before this date (ISO 8601)")
    @GetMapping
    public ResponseEntity<PagedResponseDTO<TodoResponseDTO>> list(
            @ModelAttribute TodoFilterDTO filterDTO,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        TodoFilter filter = todoMapper.toFilter(filterDTO);
        Page<Todo> page = todoUseCase.findAll(getAuthenticatedUser(), filter, pageable);
        return ResponseEntity.ok(todoMapper.toPagedResponse(page));
    }

    @Operation(summary = "List todos with cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Cursor-paginated list of todos")
    @Parameter(name = "cursor", description = "ID of the last item from the previous page")
    @Parameter(name = "size", description = "Number of items per page (default 20)")
    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listWithCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        TodoPage result = todoUseCase.listWithCursor(getAuthenticatedUser(), cursor, size);
        return ResponseEntity.ok(todoMapper.toCursorResponse(result));
    }

    @Operation(summary = "Get a todo by ID")
    @ApiResponse(responseCode = "200", description = "Todo found")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> getById(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        return ResponseEntity.ok(todoMapper.toResponse(todoUseCase.getById(getAuthenticatedUser(), id)));
    }

    @Operation(summary = "Update a todo")
    @ApiResponse(responseCode = "200", description = "Todo updated successfully")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PutMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> update(
            @Parameter(description = "Todo ID") @PathVariable Long id,
            @RequestBody @Valid TodoRequestDTO request) {
        Todo updated = todoUseCase.update(getAuthenticatedUser(), id, todoMapper.toEntity(request));
        return ResponseEntity.ok(todoMapper.toResponse(updated));
    }

    @Operation(summary = "Mark a todo as complete")
    @ApiResponse(responseCode = "200", description = "Todo marked as complete")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponseDTO> complete(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        return ResponseEntity.ok(todoMapper.toResponse(todoUseCase.complete(getAuthenticatedUser(), id)));
    }

    @Operation(summary = "Delete a todo")
    @ApiResponse(responseCode = "204", description = "Todo deleted successfully")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        todoUseCase.delete(getAuthenticatedUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
