package com.example.userservice.application.auth;

import com.example.userservice.domain.exception.InvalidCredentialsException;
import com.example.userservice.domain.exception.UserAlreadyExistsException;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.port.out.UserRepositoryPort;
import com.example.userservice.infrastructure.messaging.UserEventPublisher;
import com.example.userservice.infrastructure.security.JwtService;
import com.example.userservice.interfaces.dto.request.LoginRequestDTO;
import com.example.userservice.interfaces.dto.request.RegisterRequestDTO;
import com.example.userservice.interfaces.dto.response.AuthResponseDTO;
import com.example.userservice.interfaces.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepositoryPort userRepository;
    private final JwtService jwtService;
    private final UserEventPublisher eventPublisher;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = AuthMapper.toEntity(request, passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        String token = jwtService.generateToken(user);
        eventPublisher.publishUserRegistered(user);

        return AuthMapper.toResponse(user, token);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        eventPublisher.publishUserLogin(user);

        return AuthMapper.toResponse(user, token);
    }
}
