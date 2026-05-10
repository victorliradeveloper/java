package com.ecommerce.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Address {

    private final Long id;
    private final String street;
    private final String city;
    private final String state;
    private final String zipCode;
    private final String country;

    @Setter
    private boolean main;
}
