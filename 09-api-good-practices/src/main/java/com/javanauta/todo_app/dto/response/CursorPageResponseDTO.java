package com.javanauta.todo_app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageResponseDTO<T> {
    private List<T> content;
    private Long nextCursor;
    private boolean hasNext;
}
