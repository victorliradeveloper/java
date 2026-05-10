package com.ecommerce.auth.domain.port.in;

import com.ecommerce.auth.domain.port.in.command.LoginCommand;
import com.ecommerce.auth.domain.port.in.result.AuthResult;

public interface LoginUseCase {
    AuthResult login(LoginCommand command);
}
