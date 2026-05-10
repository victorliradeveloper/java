package com.ecommerce.user.domain.model.event;

import com.ecommerce.user.domain.model.UserProfile;

import java.time.LocalDateTime;

public record UserProfileUpdatedEvent(
        Long profileId,
        Long userId,
        String name,
        String email,
        String phone,
        LocalDateTime updatedAt
) {
    public static UserProfileUpdatedEvent from(UserProfile profile) {
        return new UserProfileUpdatedEvent(
                profile.getId(),
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getUpdatedAt()
        );
    }
}
