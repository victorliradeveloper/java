package com.studies.mongodb.documents.embedded;

public record AddressDocument(
        String street,
        String city,
        String state,
        String zipCode,
        String country
) {}
