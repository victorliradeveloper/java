package com.studies.mongodb.repositories;

import com.studies.mongodb.documents.CustomerDocument;
import com.studies.mongodb.enums.CustomerStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends MongoRepository<CustomerDocument, String> {

    // ==================== SIMPLE ====================

    Optional<CustomerDocument> findByEmail(String email);

    List<CustomerDocument> findByStatus(CustomerStatus status);

    List<CustomerDocument> findByLoyaltyPointsGreaterThan(int points);

    // ==================== MEDIUM ====================

    List<CustomerDocument> findByStatusAndLoyaltyPointsGreaterThanEqual(CustomerStatus status, int points);

    // Matches if ANY element of the addresses array has city = ?0
    // Dot-notation on array fields: MongoDB checks all elements automatically
    @Query("{ 'addresses.city': ?0 }")
    List<CustomerDocument> findByCity(String city);

    // $elemMatch ensures city AND state must match inside the SAME address object
    // Without $elemMatch: { city: SP } + { state: RJ } could match different array elements
    @Query("{ 'addresses': { $elemMatch: { 'city': ?0, 'state': ?1 } } }")
    List<CustomerDocument> findByCityAndState(String city, String state);

    // Case-insensitive name search with status filter
    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'status': ?1 }")
    List<CustomerDocument> findByNameContainingAndStatus(String namePattern, String status);

    // ==================== COMPLEX ====================

    // VIP rule: ACTIVE customers with points >= activeThreshold
    //           OR INACTIVE customers with points >= inactiveThreshold
    @Query("""
            { $or: [
                { 'status': 'ACTIVE',   'loyaltyPoints': { $gte: ?0 } },
                { 'status': 'INACTIVE', 'loyaltyPoints': { $gte: ?1 } }
            ] }
            """)
    List<CustomerDocument> findVipCustomers(int activeThreshold, int inactiveThreshold);

    // Non-suspended customers who have at least one address in the given country
    @Query("{ 'addresses': { $elemMatch: { 'country': ?0 } }, 'status': { $ne: 'SUSPENDED' } }")
    List<CustomerDocument> findActiveCustomersByCountry(String country);

    // Customers with more than one address ($expr + $size on array)
    @Query("{ $expr: { $gt: [ { $size: '$addresses' }, 1 ] } }")
    List<CustomerDocument> findCustomersWithMultipleAddresses();

    // High-value customers: status not suspended AND points above threshold AND has an address in Brazil
    @Query("""
            { $and: [
                { 'status': { $ne: 'SUSPENDED' } },
                { 'loyaltyPoints': { $gte: ?0 } },
                { 'addresses': { $elemMatch: { 'country': 'Brazil' } } }
            ] }
            """)
    List<CustomerDocument> findHighValueBrazilianCustomers(int minPoints);
}
