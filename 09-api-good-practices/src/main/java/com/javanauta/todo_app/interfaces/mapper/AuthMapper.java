package com.javanauta.todo_app.interfaces.mapper;

import com.javanauta.todo_app.domain.model.User;
import com.javanauta.todo_app.interfaces.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.interfaces.dto.response.AuthResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public User toEntity(RegisterRequestDTO request) {
        return User.builder()
                .name(request.name())
                .email(request.email())
                .password(request.password())
                .build();
    }

    public AuthResponseDTO toResponse(User user, String token) {
        return new AuthResponseDTO(user.getName(), token);
    }
}
