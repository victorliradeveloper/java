package com.ecommerce.auth.domain.port.in;

import com.ecommerce.auth.domain.port.in.command.RegisterCommand;
import com.ecommerce.auth.domain.port.in.result.AuthResult;

public interface RegisterUserUseCase {
    AuthResult register(RegisterCommand command);
}
