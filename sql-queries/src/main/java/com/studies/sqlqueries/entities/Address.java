package com.studies.sqlqueries.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// -------------------------------------------------------
// @Embeddable: a reusable value-type with no identity of its own.
//
// Used in two different ways:
//   1. Customer  → @ElementCollection → "customer_addresses" table
//                  (one row per address per customer)
//   2. Order     → @Embedded with @AttributeOverrides
//                  (address columns live directly inside the "orders" table)
//
// MongoDB equivalent: an embedded sub-document or an array element.
// -------------------------------------------------------
@Embeddable
public class Address {

    private String street;
    private String city;
    private String state;

    @Column(name = "zip_code")
    private String zipCode;

    private String country;

    public Address() {}

    public Address(String street, String city, String state, String zipCode, String country) {
        this.street  = street;
        this.city    = city;
        this.state   = state;
        this.zipCode = zipCode;
        this.country = country;
    }

    public String getStreet()  { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity()    { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState()   { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
