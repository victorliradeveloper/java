package com.ecommerce.user.domain.exception;

public class UserProfileNotFoundException extends RuntimeException {
    public UserProfileNotFoundException(Long userId) {
        super("Profile not found for user: " + userId);
    }
}
