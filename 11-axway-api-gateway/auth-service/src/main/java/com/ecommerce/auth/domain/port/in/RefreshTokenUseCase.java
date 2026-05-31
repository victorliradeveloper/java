package com.ecommerce.auth.domain.port.in;

import com.ecommerce.auth.domain.port.in.result.AuthResult;

public interface RefreshTokenUseCase {
    AuthResult refresh(String refreshToken);
}
