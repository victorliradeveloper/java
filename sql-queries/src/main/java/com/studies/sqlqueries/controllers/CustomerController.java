package com.studies.sqlqueries.controllers;

import com.studies.sqlqueries.entities.Customer;
import com.studies.sqlqueries.enums.CustomerStatus;
import com.studies.sqlqueries.services.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<Customer> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> findById(@PathVariable Long id) {
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/email/{email}")
    public ResponseEntity<Customer> findByEmail(@PathVariable String email) {
        return customerService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<Customer> findByStatus(@PathVariable CustomerStatus status) {
        return customerService.findByStatus(status);
    }

    // ==================== MEDIUM ====================

    // SQL: WHERE status = ? AND loyalty_points >= ?
    @GetMapping("/status/{status}/min-points/{minPoints}")
    public List<Customer> findByStatusAndMinPoints(
            @PathVariable CustomerStatus status,
            @PathVariable int minPoints) {
        return customerService.findByStatusAndMinPoints(status, minPoints);
    }

    // SQL JOIN to customer_addresses: matches ANY address row with the given city
    @GetMapping("/city/{city}")
    public List<Customer> findByCity(@PathVariable String city) {
        return customerService.findByCity(city);
    }

    // SQL JOIN: city AND state must be in the same customer_addresses row (same-row guarantee)
    @GetMapping("/location")
    public List<Customer> findByCityAndState(
            @RequestParam String city,
            @RequestParam String state) {
        return customerService.findByCityAndState(city, state);
    }

    // SQL ILIKE for case-insensitive name search: ?name=ali&status=ACTIVE
    @GetMapping("/search")
    public List<Customer> search(
            @RequestParam String name,
            @RequestParam String status) {
        return customerService.findByNameAndStatus(name, status);
    }

    // ==================== COMPLEX ====================

    // SQL OR: (status='ACTIVE' AND points>=activeThreshold) OR (status='INACTIVE' AND points>=inactiveThreshold)
    @GetMapping("/vip")
    public List<Customer> findVipCustomers(
            @RequestParam(defaultValue = "1000") int activeThreshold,
            @RequestParam(defaultValue = "2000") int inactiveThreshold) {
        return customerService.findVipCustomers(activeThreshold, inactiveThreshold);
    }

    // SQL JOIN + WHERE status <> 'SUSPENDED'
    @GetMapping("/country/{country}/active")
    public List<Customer> findActiveByCountry(@PathVariable String country) {
        return customerService.findActiveByCountry(country);
    }

    // SQL: WHERE (SELECT COUNT(*) FROM customer_addresses WHERE customer_id = c.id) > 1
    @GetMapping("/multiple-addresses")
    public List<Customer> findWithMultipleAddresses() {
        return customerService.findWithMultipleAddresses();
    }

    // SQL: JOIN customer_addresses + three AND conditions
    @GetMapping("/high-value/brazil")
    public List<Customer> findHighValueBrazilianCustomers(
            @RequestParam(defaultValue = "500") int minPoints) {
        return customerService.findHighValueBrazilianCustomers(minPoints);
    }
}
