package com.ecommerce.auth.infrastructure.adapter.in.web;

import com.ecommerce.auth.domain.port.in.LoginUseCase;
import com.ecommerce.auth.domain.port.in.RefreshTokenUseCase;
import com.ecommerce.auth.domain.port.in.RegisterUserUseCase;
import com.ecommerce.auth.domain.port.in.command.LoginCommand;
import com.ecommerce.auth.domain.port.in.command.RegisterCommand;
import com.ecommerce.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.ecommerce.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.ecommerce.auth.infrastructure.adapter.in.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        var command = new RegisterCommand(request.name(), request.email(), request.password());
        var result = registerUserUseCase.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        var command = new LoginCommand(request.email(), request.password());
        return ResponseEntity.ok(AuthResponse.from(loginUseCase.login(command)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(AuthResponse.from(refreshTokenUseCase.refresh(refreshToken)));
    }
}
