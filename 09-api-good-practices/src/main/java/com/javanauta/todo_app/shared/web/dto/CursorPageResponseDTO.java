package com.javanauta.todo_app.shared.web.dto;

import java.util.List;

public record CursorPageResponseDTO<T>(
        List<T> content,
        Long nextCursor,
        boolean hasNext
) {}
