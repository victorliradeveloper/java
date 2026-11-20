package com.studies.sqlqueries.controllers;

import com.studies.sqlqueries.entities.Address;
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

    // ==================== BASIC UPDATE ====================

    // PUT: substitui todos os campos — JPA: UPDATE customers SET ... WHERE id=?
    @PutMapping("/{id}")
    public ResponseEntity<Customer> fullReplace(
            @PathVariable Long id,
            @RequestBody Customer updated) {
        return ResponseEntity.ok(customerService.fullReplace(id, updated));
    }

    // PATCH: @Modifying @Query → UPDATE customers SET phone=? WHERE id=?
    @PatchMapping("/{id}/phone")
    public ResponseEntity<Customer> updatePhone(
            @PathVariable Long id,
            @RequestParam String phone) {
        return ResponseEntity.ok(customerService.updatePhone(id, phone));
    }

    // PATCH: @Modifying @Query → UPDATE customers SET status=? WHERE id=?
    @PatchMapping("/{id}/status")
    public ResponseEntity<Customer> updateStatus(
            @PathVariable Long id,
            @RequestParam CustomerStatus status) {
        return ResponseEntity.ok(customerService.updateStatus(id, status));
    }

    // ==================== MEDIUM UPDATE ====================

    // PATCH: @Modifying @Query → UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id=?
    @PatchMapping("/{id}/loyalty-points")
    public ResponseEntity<Customer> addLoyaltyPoints(
            @PathVariable Long id,
            @RequestParam int delta) {
        return ResponseEntity.ok(customerService.addLoyaltyPoints(id, delta));
    }

    // POST: carrega Customer, adiciona ao List<Address>, save() — JPA: INSERT INTO customer_addresses
    @PostMapping("/{id}/addresses")
    public ResponseEntity<Customer> addAddress(
            @PathVariable Long id,
            @RequestBody Address address) {
        return ResponseEntity.ok(customerService.addAddress(id, address));
    }

    // DELETE: carrega Customer, removeIf, save() — JPA: recria customer_addresses
    @DeleteMapping("/{id}/addresses")
    public ResponseEntity<Customer> removeAddressByCity(
            @PathVariable Long id,
            @RequestParam String city) {
        return ResponseEntity.ok(customerService.removeAddressByCity(id, city));
    }

    // ==================== ADVANCED UPDATE ====================

    // PATCH bulk: @Modifying @Query → UPDATE N clientes numa única query SQL
    // Retorna a quantidade de linhas afetadas
    @PatchMapping("/suspend-inactive")
    public ResponseEntity<Integer> suspendInactiveCustomers(
            @RequestParam(defaultValue = "100") int maxPoints) {
        return ResponseEntity.ok(customerService.suspendInactiveCustomers(maxPoints));
    }

    // PATCH: carrega Customer, localiza Address pela cidade, atualiza zipCode, salva
    // SQL equivalente ao operador posicional $ do MongoDB
    @PatchMapping("/{id}/addresses/zip")
    public ResponseEntity<Customer> updateAddressZip(
            @PathVariable Long id,
            @RequestParam String city,
            @RequestParam String newZipCode) {
        return ResponseEntity.ok(customerService.updateAddressZip(id, city, newZipCode));
    }
}
