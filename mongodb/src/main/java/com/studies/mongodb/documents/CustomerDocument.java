package com.studies.mongodb.documents;

import com.studies.mongodb.documents.embedded.AddressDocument;
import com.studies.mongodb.enums.CustomerStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "customers")
public class CustomerDocument {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private List<AddressDocument> addresses;
    private Integer loyaltyPoints;
    private CustomerStatus status;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<AddressDocument> getAddresses() { return addresses; }
    public void setAddresses(List<AddressDocument> addresses) { this.addresses = addresses; }

    public Integer getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(Integer loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
