package com.ecommerce.user.domain.port.out;

import com.ecommerce.user.domain.model.UserProfile;

import java.util.Optional;

public interface UserProfileRepository {
    Optional<UserProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    UserProfile save(UserProfile profile);
}
