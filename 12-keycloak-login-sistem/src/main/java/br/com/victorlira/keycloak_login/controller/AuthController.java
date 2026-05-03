package br.com.victorlira.keycloak_login.controller;

import br.com.victorlira.keycloak_login.dto.LoginRequest;
import br.com.victorlira.keycloak_login.dto.RegisterRequest;
import br.com.victorlira.keycloak_login.dto.TokenResponse;
import br.com.victorlira.keycloak_login.service.KeycloakService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final KeycloakService keycloakService;

    public AuthController(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(keycloakService.login(request));
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        try {
            keycloakService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }
}
