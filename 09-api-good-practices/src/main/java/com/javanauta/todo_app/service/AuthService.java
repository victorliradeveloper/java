package com.javanauta.todo_app.service;

import com.javanauta.todo_app.dto.request.LoginRequestDTO;
import com.javanauta.todo_app.dto.request.RegisterRequestDTO;
import com.javanauta.todo_app.dto.response.AuthResponseDTO;
import com.javanauta.todo_app.exception.InvalidCredentialsException;
import com.javanauta.todo_app.exception.UserAlreadyExistsException;
import com.javanauta.todo_app.model.User;
import com.javanauta.todo_app.repository.UserRepository;
import com.javanauta.todo_app.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();
        userRepository.save(user);
        return new AuthResponseDTO(user.getName(), jwtService.generateToken(user));
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponseDTO(user.getName(), jwtService.generateToken(user));
    }
}
