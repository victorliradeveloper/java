package com.example.userservice.interfaces.rest;

import com.example.userservice.domain.model.User;
import com.example.userservice.infrastructure.messaging.UserEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Operações de usuário")
public class UserController {

    private final UserEventPublisher eventPublisher;

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicitar email de redefinição de senha (requer autenticação)")
    @SecurityRequirement(name = "bearerAuth")
    public void passwordReset(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        eventPublisher.publishPasswordReset(user);
    }
}
