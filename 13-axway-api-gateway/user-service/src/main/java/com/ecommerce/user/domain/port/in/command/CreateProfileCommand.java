package com.ecommerce.user.domain.port.in.command;

public record CreateProfileCommand(Long userId, String name, String email, String phone) {}
