package com.ecommerce.auth.application.service;

import com.ecommerce.auth.domain.exception.EmailAlreadyInUseException;
import com.ecommerce.auth.domain.exception.InvalidCredentialsException;
import com.ecommerce.auth.domain.exception.InvalidTokenException;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.in.LoginUseCase;
import com.ecommerce.auth.domain.port.in.RefreshTokenUseCase;
import com.ecommerce.auth.domain.port.in.RegisterUserUseCase;
import com.ecommerce.auth.domain.port.in.command.LoginCommand;
import com.ecommerce.auth.domain.port.in.command.RegisterCommand;
import com.ecommerce.auth.domain.port.in.result.AuthResult;
import com.ecommerce.auth.domain.port.out.PasswordHasher;
import com.ecommerce.auth.domain.port.out.TokenProvider;
import com.ecommerce.auth.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements RegisterUserUseCase, LoginUseCase, RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;

    @Override
    public AuthResult register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new EmailAlreadyInUseException(command.email());
        }

        var user = User.newCustomer(
                command.name(),
                command.email(),
                passwordHasher.hash(command.password())
        );
        var saved = userRepository.save(user);
        return buildAuthResult(saved);
    }

    @Override
    public AuthResult login(LoginCommand command) {
        var user = userRepository.findByEmail(command.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(command.password(), user.getHashedPassword())) {
            throw new InvalidCredentialsException();
        }
        return buildAuthResult(user);
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        String email;
        try {
            email = tokenProvider.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!tokenProvider.isTokenValid(refreshToken, user.getEmail())) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        return buildAuthResult(user);
    }

    private AuthResult buildAuthResult(User user) {
        return new AuthResult(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user),
                tokenProvider.getAccessTokenExpiration(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
