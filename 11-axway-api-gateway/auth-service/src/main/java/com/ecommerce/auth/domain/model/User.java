package com.ecommerce.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class User {

    private final Long id;
    private final String name;
    private final String email;
    private final String hashedPassword;
    private final Role role;
    private final LocalDateTime createdAt;

    public static User newCustomer(String name, String email, String hashedPassword) {
        return User.builder()
                .name(name)
                .email(email)
                .hashedPassword(hashedPassword)
                .role(Role.CUSTOMER)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
