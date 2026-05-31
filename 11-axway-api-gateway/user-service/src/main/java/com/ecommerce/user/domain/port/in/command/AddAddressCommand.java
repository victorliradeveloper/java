package com.ecommerce.user.domain.port.in.command;

public record AddAddressCommand(
        Long userId,
        String street,
        String city,
        String state,
        String zipCode,
        String country,
        boolean main
) {}
