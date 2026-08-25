package com.javanauta.todo_app.todo.infrastructure.persistence;

import com.javanauta.todo_app.auth.domain.model.User;
import com.javanauta.todo_app.todo.domain.model.Todo;
import com.javanauta.todo_app.todo.infrastructure.persistence.repository.TodoJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test that proves the partial+functional unique index from
 * V3__add_unique_active_todo_title.sql behaves as specified, against a real
 * PostgreSQL instance (H2 cannot express partial indexes, so this can only be
 * verified here). Flyway builds the schema; Hibernate runs in validate mode.
 *
 * <p>Inserts go through {@link TodoJpaRepository#saveAndFlush} — the same path
 * {@code TodoService} uses — so a constraint violation surfaces as Spring's
 * {@link DataIntegrityViolationException}, exactly as the service expects to catch it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TodoTitleUniqueConstraintIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private TodoJpaRepository todoRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void sameActiveTitle_sameCase_sameUser_violatesUniqueIndex() {
        User user = persistUser("alice@email.com");
        todoRepository.saveAndFlush(activeTodo(user, "Buy milk"));

        assertThatThrownBy(() -> todoRepository.saveAndFlush(activeTodo(user, "Buy milk")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameActiveTitle_differentCase_sameUser_violatesUniqueIndex() {
        User user = persistUser("bob@email.com");
        todoRepository.saveAndFlush(activeTodo(user, "Buy milk"));

        assertThatThrownBy(() -> todoRepository.saveAndFlush(activeTodo(user, "BUY MILK")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTitle_whenPreviousIsCompleted_isAllowed() {
        User user = persistUser("carol@email.com");
        Todo first = todoRepository.saveAndFlush(activeTodo(user, "Buy milk"));
        first.setCompleted(true);
        todoRepository.saveAndFlush(first); // leaves the partial index (WHERE completed = false)

        assertThatCode(() -> todoRepository.saveAndFlush(activeTodo(user, "Buy milk")))
                .doesNotThrowAnyException();
    }

    @Test
    void sameActiveTitle_differentUsers_isAllowed() {
        User userA = persistUser("dave@email.com");
        User userB = persistUser("erin@email.com");
        todoRepository.saveAndFlush(activeTodo(userA, "Buy milk"));

        assertThatCode(() -> todoRepository.saveAndFlush(activeTodo(userB, "Buy milk")))
                .doesNotThrowAnyException();
    }

    private User persistUser(String email) {
        return em.persistFlushFind(User.builder()
                .name("Test")
                .email(email)
                .password("hash")
                .build());
    }

    private Todo activeTodo(User user, String title) {
        return Todo.builder()
                .title(title)
                .user(user)
                .build(); // completed=false and createdAt are set by @PrePersist
    }
}
