package com.javanauta.todo_app.auth.domain.port.out;

import com.javanauta.todo_app.auth.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
