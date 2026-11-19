package com.studies.mongodb.services;

import com.studies.mongodb.documents.CustomerDocument;
import com.studies.mongodb.enums.CustomerStatus;
import com.studies.mongodb.repositories.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerDocument> findAll() {
        return customerRepository.findAll();
    }

    public Optional<CustomerDocument> findById(String id) {
        return customerRepository.findById(id);
    }

    public Optional<CustomerDocument> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public List<CustomerDocument> findByStatus(CustomerStatus status) {
        return customerRepository.findByStatus(status);
    }

    public List<CustomerDocument> findByStatusAndMinPoints(CustomerStatus status, int minPoints) {
        return customerRepository.findByStatusAndLoyaltyPointsGreaterThanEqual(status, minPoints);
    }

    public List<CustomerDocument> findByCity(String city) {
        return customerRepository.findByCity(city);
    }

    public List<CustomerDocument> findByCityAndState(String city, String state) {
        return customerRepository.findByCityAndState(city, state);
    }

    public List<CustomerDocument> findByNameAndStatus(String namePattern, String status) {
        return customerRepository.findByNameContainingAndStatus(namePattern, status);
    }

    public List<CustomerDocument> findVipCustomers(int activeThreshold, int inactiveThreshold) {
        return customerRepository.findVipCustomers(activeThreshold, inactiveThreshold);
    }

    public List<CustomerDocument> findActiveByCountry(String country) {
        return customerRepository.findActiveCustomersByCountry(country);
    }

    public List<CustomerDocument> findWithMultipleAddresses() {
        return customerRepository.findCustomersWithMultipleAddresses();
    }

    public List<CustomerDocument> findHighValueBrazilianCustomers(int minPoints) {
        return customerRepository.findHighValueBrazilianCustomers(minPoints);
    }
}
