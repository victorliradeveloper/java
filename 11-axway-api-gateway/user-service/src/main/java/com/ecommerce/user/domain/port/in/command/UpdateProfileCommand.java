package com.ecommerce.user.domain.port.in.command;

public record UpdateProfileCommand(Long userId, String name, String email, String phone) {}
