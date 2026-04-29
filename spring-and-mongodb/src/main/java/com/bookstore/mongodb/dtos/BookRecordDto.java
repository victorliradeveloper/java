package com.bookstore.mongodb.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record BookRecordDto(
        @NotBlank String title,
        @NotNull String publisherId,
        @NotNull Set<String> authorIds,
        @NotBlank String reviewComment
) {
}
