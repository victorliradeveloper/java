package com.javanauta.todo_app.auth.domain.port.in;

import com.javanauta.todo_app.auth.domain.model.User;

public interface AuthUseCase {

    User register(User user);

    User authenticate(String email, String rawPassword);
}
