package com.ecommerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserProfileRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone
) {}
