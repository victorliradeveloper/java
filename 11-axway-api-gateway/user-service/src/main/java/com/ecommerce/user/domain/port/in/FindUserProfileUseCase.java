package com.ecommerce.user.domain.port.in;

import com.ecommerce.user.domain.model.UserProfile;

public interface FindUserProfileUseCase {
    UserProfile findByUserId(Long userId);
}
