package com.ecommerce.auth.infrastructure.adapter.out.persistence;

import com.ecommerce.auth.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceMapper {

    public UserJpaEntity toJpaEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .password(user.getHashedPassword())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public User toDomain(UserJpaEntity entity) {
        return User.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .hashedPassword(entity.getPassword())
                .role(entity.getRole())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
