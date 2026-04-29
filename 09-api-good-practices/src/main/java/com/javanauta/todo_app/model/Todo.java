package com.javanauta.todo_app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "todo", indexes = {
        @Index(name = "idx_todo_completed", columnList = "completed"),
        @Index(name = "idx_todo_due_date", columnList = "due_date"),
        @Index(name = "idx_todo_completed_due_date", columnList = "completed, due_date")
})
@Entity
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "completed", nullable = false, columnDefinition = "boolean not null default false")
    private boolean completed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.completed = false;
    }
}
