package com.ecommerce.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class UserProfile {

    private final Long id;
    private final Long userId;
    private String name;
    private String email;
    private String phone;
    @Builder.Default
    private final List<Address> addresses = new ArrayList<>();
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfile newProfile(Long userId, String name, String email, String phone) {
        var now = LocalDateTime.now();
        return UserProfile.builder()
                .userId(userId)
                .name(name)
                .email(email)
                .phone(phone)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void update(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public void addAddress(Address address) {
        if (address.isMain()) {
            addresses.forEach(a -> a.setMain(false));
        }
        addresses.add(address);
        this.updatedAt = LocalDateTime.now();
    }
}
