package com.ecommerce.auth.domain.port.in.command;

public record RegisterCommand(String name, String email, String password) {}
