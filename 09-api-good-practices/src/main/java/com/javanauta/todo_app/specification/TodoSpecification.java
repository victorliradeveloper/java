package com.javanauta.todo_app.specification;

import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.model.Todo;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TodoSpecification {

    private TodoSpecification() {}

    public static Specification<Todo> withFilters(TodoFilterDTO filter) {
        return Specification
                .where(titleLike(filter.getTitle()))
                .and(completedEquals(filter.getCompleted()))
                .and(dueDateFrom(filter.getDueDateFrom()))
                .and(dueDateTo(filter.getDueDateTo()));
    }

    private static Specification<Todo> titleLike(String title) {
        return (root, query, cb) -> title == null || title.isBlank() ? null
                : cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    private static Specification<Todo> completedEquals(Boolean completed) {
        return (root, query, cb) -> completed == null ? null
                : cb.equal(root.get("completed"), completed);
    }

    private static Specification<Todo> dueDateFrom(LocalDateTime from) {
        return (root, query, cb) -> from == null ? null
                : cb.greaterThanOrEqualTo(root.get("dueDate"), from);
    }

    private static Specification<Todo> dueDateTo(LocalDateTime to) {
        return (root, query, cb) -> to == null ? null
                : cb.lessThanOrEqualTo(root.get("dueDate"), to);
    }
}
