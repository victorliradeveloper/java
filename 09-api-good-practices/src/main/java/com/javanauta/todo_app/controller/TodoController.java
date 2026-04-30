package com.javanauta.todo_app.controller;

import com.javanauta.todo_app.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.dto.ErrorResponseDTO;
import com.javanauta.todo_app.dto.PagedResponseDTO;
import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.dto.TodoRequestDTO;
import com.javanauta.todo_app.dto.TodoResponseDTO;
import com.javanauta.todo_app.model.User;
import com.javanauta.todo_app.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final TodoService todoService;

    @Operation(summary = "Create a new todo")
    @ApiResponse(responseCode = "201", description = "Todo created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.create(getAuthenticatedUser(), request));
    }

    @Operation(summary = "List todos with pagination and filters")
    @ApiResponse(responseCode = "200", description = "Paginated list of todos")
    @Parameter(name = "title", description = "Filter by title (partial match)")
    @Parameter(name = "completed", description = "Filter by completion status")
    @Parameter(name = "dueDateFrom", description = "Filter todos due after this date (ISO 8601)")
    @Parameter(name = "dueDateTo", description = "Filter todos due before this date (ISO 8601)")
    @GetMapping
    public ResponseEntity<PagedResponseDTO<TodoResponseDTO>> list(
            @ModelAttribute TodoFilterDTO filter,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(todoService.findAll(getAuthenticatedUser(), filter, pageable));
    }

    @Operation(summary = "List todos with cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Cursor-paginated list of todos")
    @Parameter(name = "cursor", description = "ID of the last item from the previous page")
    @Parameter(name = "size", description = "Number of items per page (default 20)")
    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listWithCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(todoService.listWithCursor(getAuthenticatedUser(), cursor, size));
    }

    @Operation(summary = "Get a todo by ID")
    @ApiResponse(responseCode = "200", description = "Todo found")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @GetMapping("/{id}")
    public ResponseEntity<TodoResponseDTO> getById(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        return ResponseEntity.ok(todoService.getById(getAuthenticatedUser(), id));
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
        return ResponseEntity.ok(todoService.update(getAuthenticatedUser(), id, request));
    }

    @Operation(summary = "Mark a todo as complete")
    @ApiResponse(responseCode = "200", description = "Todo marked as complete")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponseDTO> complete(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        return ResponseEntity.ok(todoService.complete(getAuthenticatedUser(), id));
    }

    @Operation(summary = "Delete a todo")
    @ApiResponse(responseCode = "204", description = "Todo deleted successfully")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Todo ID") @PathVariable Long id) {
        todoService.delete(getAuthenticatedUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
