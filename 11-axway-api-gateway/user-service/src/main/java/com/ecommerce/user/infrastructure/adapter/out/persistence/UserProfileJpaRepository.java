package com.ecommerce.user.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long> {
    Optional<UserProfileJpaEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
