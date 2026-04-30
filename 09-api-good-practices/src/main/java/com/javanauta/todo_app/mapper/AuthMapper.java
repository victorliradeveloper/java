package com.javanauta.todo_app.mapper;

import com.javanauta.todo_app.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.dto.response.AuthResponseDTO;
import com.javanauta.todo_app.model.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public User toEntity(RegisterRequestDTO request, String encodedPassword) {
        return User.builder()
                .name(request.name())
                .email(request.email())
                .password(encodedPassword)
                .build();
    }

    public AuthResponseDTO toResponse(User user, String token) {
        return new AuthResponseDTO(user.getName(), token);
    }
}
