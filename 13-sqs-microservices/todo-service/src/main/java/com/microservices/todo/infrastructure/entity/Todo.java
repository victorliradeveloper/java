package com.microservices.todo.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "todos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Todo {

    @Id
    private String id;

    private String title;

    private String description;

    private boolean completed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
