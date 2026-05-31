package com.ecommerce.auth.infrastructure.adapter.out.persistence;

import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        var entity = mapper.toJpaEntity(user);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
