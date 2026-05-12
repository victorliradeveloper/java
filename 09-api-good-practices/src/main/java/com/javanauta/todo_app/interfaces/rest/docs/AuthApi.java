package com.javanauta.todo_app.interfaces.rest.docs;

import com.javanauta.todo_app.interfaces.dto.request.LoginRequestDTO;
import com.javanauta.todo_app.interfaces.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.AuthResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.ErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth", description = "Registration and authentication")
public interface AuthApi {

    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<AuthResponseDTO> register(@RequestBody @Valid RegisterRequestDTO request);

    @Operation(summary = "Authenticate and retrieve JWT token")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO request);
}
