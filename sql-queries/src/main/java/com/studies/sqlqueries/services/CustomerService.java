package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Customer;
import com.studies.sqlqueries.enums.CustomerStatus;
import com.studies.sqlqueries.repositories.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public List<Customer> findByStatus(CustomerStatus status) {
        return customerRepository.findByStatus(status);
    }

    public List<Customer> findByStatusAndMinPoints(CustomerStatus status, int minPoints) {
        return customerRepository.findByStatusAndLoyaltyPointsGreaterThanEqual(status, minPoints);
    }

    public List<Customer> findByCity(String city) {
        return customerRepository.findByCity(city);
    }

    public List<Customer> findByCityAndState(String city, String state) {
        return customerRepository.findByCityAndState(city, state);
    }

    public List<Customer> findByNameAndStatus(String namePattern, String status) {
        return customerRepository.findByNameContainingAndStatus(namePattern, status);
    }

    public List<Customer> findVipCustomers(int activeThreshold, int inactiveThreshold) {
        return customerRepository.findVipCustomers(activeThreshold, inactiveThreshold);
    }

    public List<Customer> findActiveByCountry(String country) {
        return customerRepository.findActiveCustomersByCountry(country);
    }

    public List<Customer> findWithMultipleAddresses() {
        return customerRepository.findCustomersWithMultipleAddresses();
    }

    public List<Customer> findHighValueBrazilianCustomers(int minPoints) {
        return customerRepository.findHighValueBrazilianCustomers(minPoints);
    }
}
