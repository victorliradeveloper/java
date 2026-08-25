package com.example.userservice.interfaces.mapper;

import com.example.userservice.domain.model.User;
import com.example.userservice.interfaces.dto.request.RegisterRequestDTO;
import com.example.userservice.interfaces.dto.response.AuthResponseDTO;

public class AuthMapper {

    private AuthMapper() {}

    public static User toEntity(RegisterRequestDTO dto, String encodedPassword) {
        return User.builder()
                .name(dto.name())
                .email(dto.email())
                .password(encodedPassword)
                .build();
    }

    public static AuthResponseDTO toResponse(User user, String token) {
        return new AuthResponseDTO(user.getName(), token);
    }
}
