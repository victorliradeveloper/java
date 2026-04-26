package com.microservices.todo.dto.request;

public record TodoUpdateDTO(
        String title,
        String description,
        Boolean completed
) {}
