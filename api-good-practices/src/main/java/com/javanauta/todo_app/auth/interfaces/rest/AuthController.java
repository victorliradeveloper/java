package com.javanauta.todo_app.auth.interfaces.rest;

import com.javanauta.todo_app.auth.domain.model.User;
import com.javanauta.todo_app.auth.domain.port.in.AuthUseCase;
import com.javanauta.todo_app.auth.infrastructure.security.JwtService;
import com.javanauta.todo_app.auth.interfaces.dto.request.LoginRequestDTO;
import com.javanauta.todo_app.auth.interfaces.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.auth.interfaces.dto.response.AuthResponseDTO;
import com.javanauta.todo_app.auth.interfaces.mapper.AuthMapper;
import com.javanauta.todo_app.auth.interfaces.rest.docs.AuthApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthUseCase authUseCase;
    private final AuthMapper authMapper;
    private final JwtService jwtService;

    @PostMapping("/register")
    @Override
    public ResponseEntity<AuthResponseDTO> register(@RequestBody @Valid RegisterRequestDTO request) {
        User saved = authUseCase.register(authMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authMapper.toResponse(saved, jwtService.generateToken(saved)));
    }

    @PostMapping("/login")
    @Override
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO request) {
        User user = authUseCase.authenticate(request.email(), request.password());
        return ResponseEntity.ok(authMapper.toResponse(user, jwtService.generateToken(user)));
    }
}
