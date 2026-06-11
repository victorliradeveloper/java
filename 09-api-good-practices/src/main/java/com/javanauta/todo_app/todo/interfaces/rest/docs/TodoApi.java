package com.javanauta.todo_app.todo.interfaces.rest.docs;

import com.javanauta.todo_app.todo.interfaces.dto.request.TodoFilterDTO;
import com.javanauta.todo_app.todo.interfaces.dto.request.TodoRequestDTO;
import com.javanauta.todo_app.shared.web.dto.CursorPageResponseDTO;
import com.javanauta.todo_app.shared.web.dto.ErrorResponseDTO;
import com.javanauta.todo_app.shared.web.dto.PagedResponseDTO;
import com.javanauta.todo_app.todo.interfaces.dto.response.TodoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Todos", description = "Todo management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "403", description = "Authenticated user is not allowed to access this resource",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "429", description = "Rate limit exceeded (100 requests/minute per user)",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "500", description = "Unexpected internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public interface TodoApi {

    @Operation(summary = "Create a new todo")
    @ApiResponse(responseCode = "201", description = "Todo created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Todo limit per user exceeded, or an active todo with the same title already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "422", description = "Due date is in the past",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request);

    @Operation(summary = "List todos with pagination and filters")
    @ApiResponse(responseCode = "200", description = "Paginated list of todos")
    ResponseEntity<PagedResponseDTO<TodoResponseDTO>> list(
            @ModelAttribute TodoFilterDTO filterDTO,
            @PageableDefault(size = 20, sort = "id") Pageable pageable);

    @Operation(summary = "List todos with cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Cursor-paginated list of todos")
    @ApiResponse(responseCode = "400", description = "Invalid cursor (must be non-negative)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<CursorPageResponseDTO<TodoResponseDTO>> listWithCursor(
            @Parameter(description = "ID of the last item from the previous page")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "Number of items per page (default 20)")
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Get a todo by ID")
    @ApiResponse(responseCode = "200", description = "Todo found")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<TodoResponseDTO> getById(
            @Parameter(description = "Todo ID") @PathVariable Long id);

    @Operation(summary = "Update a todo")
    @ApiResponse(responseCode = "200", description = "Todo updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Todo is already completed and cannot be modified",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "422", description = "Due date is in the past",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<TodoResponseDTO> update(
            @Parameter(description = "Todo ID") @PathVariable Long id,
            @RequestBody @Valid TodoRequestDTO request);

    @Operation(summary = "Mark a todo as complete")
    @ApiResponse(responseCode = "200", description = "Todo marked as complete")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<TodoResponseDTO> complete(
            @Parameter(description = "Todo ID") @PathVariable Long id);

    @Operation(summary = "Delete a todo")
    @ApiResponse(responseCode = "204", description = "Todo deleted successfully")
    @ApiResponse(responseCode = "404", description = "Todo not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<Void> delete(
            @Parameter(description = "Todo ID") @PathVariable Long id);
}
