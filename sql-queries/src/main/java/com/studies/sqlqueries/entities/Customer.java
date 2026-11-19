package com.studies.sqlqueries.entities;

import com.studies.sqlqueries.enums.CustomerStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// -------------------------------------------------------
// Tables produced by this entity:
//
//   customers
//     id | name | email | phone | loyalty_points | status | created_at
//
//   customer_addresses  (one row per address per customer)
//     customer_id | street | city | state | zip_code | country
//
// MongoDB equivalent: @Document(collection = "customers") with
// an embedded addresses array.
// -------------------------------------------------------
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // SQL: UNIQUE constraint → implicit index (equivalent to MongoDB @Indexed(unique=true))
    @Column(unique = true)
    private String email;

    private String phone;

    // @ElementCollection creates the "customer_addresses" join table.
    // FetchType.LAZY + open-in-view=true: the rows are fetched on first access,
    // which happens during JSON serialization inside the HTTP request scope.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "customer_addresses", joinColumns = @JoinColumn(name = "customer_id"))
    private List<Address> addresses = new ArrayList<>();

    @Column(name = "loyalty_points")
    private Integer loyaltyPoints;

    // EnumType.STRING stores the enum name as a VARCHAR, e.g. 'ACTIVE'
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId()   { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName()  { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public Integer getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(Integer loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
