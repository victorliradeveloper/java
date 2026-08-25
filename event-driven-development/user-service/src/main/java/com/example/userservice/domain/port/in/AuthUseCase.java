package com.example.userservice.domain.port.in;

import com.example.userservice.interfaces.dto.request.LoginRequestDTO;
import com.example.userservice.interfaces.dto.request.RegisterRequestDTO;
import com.example.userservice.interfaces.dto.response.AuthResponseDTO;

public interface AuthUseCase {
    AuthResponseDTO register(RegisterRequestDTO request);
    AuthResponseDTO login(LoginRequestDTO request);
}
