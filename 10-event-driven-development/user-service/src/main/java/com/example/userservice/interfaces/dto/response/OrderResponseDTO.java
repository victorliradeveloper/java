package com.example.userservice.interfaces.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        String description,
        BigDecimal amount,
        Instant createdAt
) {}
