package br.com.victorlira.keycloak_login.dto;

public record RegisterRequest(
        String username,
        String email,
        String firstName,
        String lastName,
        String password
) {}
