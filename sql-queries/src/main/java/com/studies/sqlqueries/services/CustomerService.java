package com.studies.sqlqueries.services;

import com.studies.sqlqueries.entities.Address;
import com.studies.sqlqueries.entities.Customer;
import com.studies.sqlqueries.enums.CustomerStatus;
import com.studies.sqlqueries.repositories.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // ==================== BASIC UPDATE ====================

    // save() — carrega a entidade, substitui TODOS os campos e salva.
    // JPA gera: UPDATE customers SET name=?, email=?, phone=?, ... WHERE id=?
    // MongoDB equivalente: fullReplace via repository.save()
    @Transactional
    public Customer fullReplace(Long id, Customer updated) {
        var existing = findByIdOrThrow(id);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        return customerRepository.save(updated);
    }

    // @Modifying @Query → UPDATE customers SET phone = ? WHERE id = ?
    // MongoDB: $set — atualiza apenas um campo sem carregar o documento inteiro.
    // @Transactional é obrigatório para operações @Modifying.
    @Transactional
    public Customer updatePhone(Long id, String phone) {
        findByIdOrThrow(id);
        customerRepository.updatePhone(id, phone);
        return findByIdOrThrow(id);
    }

    // @Modifying @Query → UPDATE customers SET status = ? WHERE id = ?
    // MongoDB: $set no campo status
    @Transactional
    public Customer updateStatus(Long id, CustomerStatus status) {
        findByIdOrThrow(id);
        customerRepository.updateStatus(id, status);
        return findByIdOrThrow(id);
    }

    // ==================== MEDIUM UPDATE ====================

    // @Modifying @Query → UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?
    // MongoDB: $inc — operação atômica sem carregar o documento.
    @Transactional
    public Customer addLoyaltyPoints(Long id, int delta) {
        findByIdOrThrow(id);
        customerRepository.addLoyaltyPoints(id, delta);
        return findByIdOrThrow(id);
    }

    // $push equivalente: carrega a entidade, adiciona ao List<Address> e salva.
    // JPA gerencia a tabela customer_addresses: DELETE todas as linhas do customer + INSERT das atuais.
    // Diferente do MongoDB $push que insere APENAS a nova linha na coleção.
    @Transactional
    public Customer addAddress(Long id, Address address) {
        var customer = findByIdOrThrow(id);
        customer.getAddresses().add(address);
        return customerRepository.save(customer);
    }

    // $pull equivalente: carrega, remove do List por cidade e salva.
    // JPA recria todas as linhas da tabela customer_addresses — menos eficiente que $pull no MongoDB.
    @Transactional
    public Customer removeAddressByCity(Long id, String city) {
        var customer = findByIdOrThrow(id);
        customer.getAddresses().removeIf(a -> a.getCity().equals(city));
        return customerRepository.save(customer);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti equivalente: @Modifying @Query → UPDATE N linhas de uma vez.
    // JPA gera: UPDATE customers SET status=? WHERE status=? AND loyalty_points < ?
    // MongoDB: mongoTemplate.updateMulti(query, update, Class)
    @Transactional
    public int suspendInactiveCustomers(int maxPoints) {
        return customerRepository.bulkUpdateStatus(
                CustomerStatus.INACTIVE, CustomerStatus.SUSPENDED, maxPoints);
    }

    // Operador posicional $ equivalente: carrega a entidade, localiza o Address pela cidade,
    // atualiza o zipCode e salva. JPA não tem um operador posicional nativo —
    // a lógica de "qual elemento do array alterar" fica na aplicação.
    @Transactional
    public Customer updateAddressZip(Long id, String city, String newZipCode) {
        var customer = findByIdOrThrow(id);
        customer.getAddresses().stream()
                .filter(a -> a.getCity().equals(city))
                .forEach(a -> a.setZipCode(newZipCode));
        return customerRepository.save(customer);
    }

    private Customer findByIdOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }
}
