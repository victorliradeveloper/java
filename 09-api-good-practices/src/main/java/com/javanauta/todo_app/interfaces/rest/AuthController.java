package com.javanauta.todo_app.interfaces.rest;

import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.domain.port.in.AuthUseCase;
import com.javanauta.todo_app.infrastructure.security.JwtService;
import com.javanauta.todo_app.interfaces.dto.request.LoginRequestDTO;
import com.javanauta.todo_app.interfaces.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.AuthResponseDTO;
import com.javanauta.todo_app.interfaces.dto.response.ErrorResponseDTO;
import com.javanauta.todo_app.interfaces.mapper.AuthMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Registration and authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;
    private final AuthMapper authMapper;
    private final JwtService jwtService;

    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody @Valid RegisterRequestDTO request) {
        User saved = authUseCase.register(authMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authMapper.toResponse(saved, jwtService.generateToken(saved)));
    }

    @Operation(summary = "Authenticate and retrieve JWT token")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO request) {
        User user = authUseCase.authenticate(request.email(), request.password());
        return ResponseEntity.ok(authMapper.toResponse(user, jwtService.generateToken(user)));
    }
}
