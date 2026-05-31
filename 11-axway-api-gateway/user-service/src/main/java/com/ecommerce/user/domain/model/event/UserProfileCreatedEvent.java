package com.ecommerce.user.domain.model.event;

import com.ecommerce.user.domain.model.UserProfile;

import java.time.LocalDateTime;

public record UserProfileCreatedEvent(
        Long profileId,
        Long userId,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {
    public static UserProfileCreatedEvent from(UserProfile profile) {
        return new UserProfileCreatedEvent(
                profile.getId(),
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getCreatedAt()
        );
    }
}
