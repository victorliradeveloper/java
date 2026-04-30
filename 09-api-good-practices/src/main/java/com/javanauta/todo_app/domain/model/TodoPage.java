package com.javanauta.todo_app.domain.model;

import java.util.List;

public record TodoPage(List<Todo> content, Long nextCursor, boolean hasNext) {}
