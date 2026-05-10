package com.ecommerce.user.infrastructure.adapter.out.persistence;

import com.ecommerce.user.domain.model.Address;
import com.ecommerce.user.domain.model.UserProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class UserProfilePersistenceMapper {

    public UserProfileJpaEntity toJpaEntity(UserProfile profile) {
        var entity = UserProfileJpaEntity.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .name(profile.getName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .addresses(new ArrayList<>())
                .build();

        profile.getAddresses().forEach(address -> {
            var addressEntity = AddressJpaEntity.builder()
                    .id(address.getId())
                    .userProfile(entity)
                    .street(address.getStreet())
                    .city(address.getCity())
                    .state(address.getState())
                    .zipCode(address.getZipCode())
                    .country(address.getCountry())
                    .main(address.isMain())
                    .build();
            entity.getAddresses().add(addressEntity);
        });

        return entity;
    }

    public UserProfile toDomain(UserProfileJpaEntity entity) {
        var profile = UserProfile.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .addresses(new ArrayList<>())
                .build();

        entity.getAddresses().forEach(addressEntity -> profile.getAddresses().add(toDomainAddress(addressEntity)));
        return profile;
    }

    private Address toDomainAddress(AddressJpaEntity entity) {
        return Address.builder()
                .id(entity.getId())
                .street(entity.getStreet())
                .city(entity.getCity())
                .state(entity.getState())
                .zipCode(entity.getZipCode())
                .country(entity.getCountry())
                .main(entity.isMain())
                .build();
    }
}
