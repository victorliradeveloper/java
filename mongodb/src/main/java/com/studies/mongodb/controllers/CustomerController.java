package com.studies.mongodb.controllers;

import com.studies.mongodb.documents.CustomerDocument;
import com.studies.mongodb.enums.CustomerStatus;
import com.studies.mongodb.services.CustomerService;
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
    public List<CustomerDocument> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDocument> findById(@PathVariable String id) {
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== SIMPLE ====================

    @GetMapping("/email/{email}")
    public ResponseEntity<CustomerDocument> findByEmail(@PathVariable String email) {
        return customerService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<CustomerDocument> findByStatus(@PathVariable CustomerStatus status) {
        return customerService.findByStatus(status);
    }

    // ==================== MEDIUM ====================

    // status + minimum loyalty points: /status/ACTIVE/min-points/500
    @GetMapping("/status/{status}/min-points/{minPoints}")
    public List<CustomerDocument> findByStatusAndMinPoints(
            @PathVariable CustomerStatus status,
            @PathVariable int minPoints) {
        return customerService.findByStatusAndMinPoints(status, minPoints);
    }

    // Dot-notation on array: matches ANY address with the given city
    @GetMapping("/city/{city}")
    public List<CustomerDocument> findByCity(@PathVariable String city) {
        return customerService.findByCity(city);
    }

    // $elemMatch: city AND state must be in the SAME address element
    @GetMapping("/location")
    public List<CustomerDocument> findByCityAndState(
            @RequestParam String city,
            @RequestParam String state) {
        return customerService.findByCityAndState(city, state);
    }

    // Regex name search + status: ?name=ali&status=ACTIVE
    @GetMapping("/search")
    public List<CustomerDocument> search(
            @RequestParam String name,
            @RequestParam String status) {
        return customerService.findByNameAndStatus(name, status);
    }

    // ==================== COMPLEX ====================

    // VIP: active >= activeThreshold OR inactive >= inactiveThreshold
    @GetMapping("/vip")
    public List<CustomerDocument> findVipCustomers(
            @RequestParam(defaultValue = "1000") int activeThreshold,
            @RequestParam(defaultValue = "2000") int inactiveThreshold) {
        return customerService.findVipCustomers(activeThreshold, inactiveThreshold);
    }

    // Non-suspended customers in a country
    @GetMapping("/country/{country}/active")
    public List<CustomerDocument> findActiveByCountry(@PathVariable String country) {
        return customerService.findActiveByCountry(country);
    }

    // $expr + $size on addresses array
    @GetMapping("/multiple-addresses")
    public List<CustomerDocument> findWithMultipleAddresses() {
        return customerService.findWithMultipleAddresses();
    }

    // High-value Brazilian customers: not suspended + min points + address in Brazil
    @GetMapping("/high-value/brazil")
    public List<CustomerDocument> findHighValueBrazilianCustomers(
            @RequestParam(defaultValue = "500") int minPoints) {
        return customerService.findHighValueBrazilianCustomers(minPoints);
    }
}
