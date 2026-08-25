package com.javanauta.todo_app.shared.web.dto;

import java.util.List;

public record PagedResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {}
