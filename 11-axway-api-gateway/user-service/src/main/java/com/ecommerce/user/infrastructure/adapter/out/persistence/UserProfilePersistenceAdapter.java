package com.ecommerce.user.infrastructure.adapter.out.persistence;

import com.ecommerce.user.domain.model.UserProfile;
import com.ecommerce.user.domain.port.out.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserProfilePersistenceAdapter implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfilePersistenceMapper mapper;

    @Override
    public Optional<UserProfile> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return jpaRepository.existsByUserId(userId);
    }

    @Override
    public UserProfile save(UserProfile profile) {
        var entity = mapper.toJpaEntity(profile);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
