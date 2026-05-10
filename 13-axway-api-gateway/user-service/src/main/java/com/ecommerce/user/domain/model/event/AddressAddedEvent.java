package com.ecommerce.user.domain.model.event;

import com.ecommerce.user.domain.model.Address;
import com.ecommerce.user.domain.model.UserProfile;

public record AddressAddedEvent(
        Long profileId,
        Long userId,
        Long addressId,
        String street,
        String city,
        String state,
        String zipCode,
        String country,
        boolean main
) {
    public static AddressAddedEvent of(UserProfile profile, Address address) {
        return new AddressAddedEvent(
                profile.getId(),
                profile.getUserId(),
                address.getId(),
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getZipCode(),
                address.getCountry(),
                address.isMain()
        );
    }
}
