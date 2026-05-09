package com.ecommerce.user.dto;

import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String phone,
        List<AddressResponse> addresses,
        LocalDateTime createdAt
) {
    public record AddressResponse(
            Long id,
            String street,
            String city,
            String state,
            String zipCode,
            String country,
            boolean main
    ) {
        static AddressResponse from(Address address) {
            return new AddressResponse(
                    address.getId(), address.getStreet(), address.getCity(),
                    address.getState(), address.getZipCode(), address.getCountry(), address.isMain()
            );
        }
    }

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getAddresses().stream().map(AddressResponse::from).toList(),
                profile.getCreatedAt()
        );
    }
}
