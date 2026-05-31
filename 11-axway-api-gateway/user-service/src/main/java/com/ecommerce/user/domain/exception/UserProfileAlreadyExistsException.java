package com.ecommerce.user.domain.exception;

public class UserProfileAlreadyExistsException extends RuntimeException {
    public UserProfileAlreadyExistsException(Long userId) {
        super("Profile already exists for user: " + userId);
    }
}
