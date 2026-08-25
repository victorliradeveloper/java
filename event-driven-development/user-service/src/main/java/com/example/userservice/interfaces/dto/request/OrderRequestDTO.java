package com.example.userservice.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderRequestDTO(

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) {}
