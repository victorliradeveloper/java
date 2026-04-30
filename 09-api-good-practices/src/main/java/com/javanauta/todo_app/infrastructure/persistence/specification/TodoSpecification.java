package com.javanauta.todo_app.infrastructure.persistence.specification;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.TodoFilter;
import com.javanauta.todo_app.domain.model.User;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TodoSpecification {

    private TodoSpecification() {}

    public static Specification<Todo> withFilters(TodoFilter filter, User user) {
        return Specification
                .where(userEquals(user))
                .and(titleLike(filter.title()))
                .and(completedEquals(filter.completed()))
                .and(dueDateFrom(filter.dueDateFrom()))
                .and(dueDateTo(filter.dueDateTo()));
    }

    private static Specification<Todo> userEquals(User user) {
        return (root, query, cb) -> cb.equal(root.get("user"), user);
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
