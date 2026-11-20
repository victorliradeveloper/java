package com.studies.mongodb.services;

import com.studies.mongodb.documents.CustomerDocument;
import com.studies.mongodb.documents.embedded.AddressDocument;
import com.studies.mongodb.enums.CustomerStatus;
import com.studies.mongodb.repositories.CustomerRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final MongoTemplate mongoTemplate;

    public CustomerService(CustomerRepository customerRepository, MongoTemplate mongoTemplate) {
        this.customerRepository = customerRepository;
        this.mongoTemplate = mongoTemplate;
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

    // ==================== BASIC UPDATE ====================

    // save() — carrega o documento, substitui TODOS os campos e salva de volta
    // Campos não enviados se tornam null — cuidado com dados que você não quer apagar
    public CustomerDocument fullReplace(String id, CustomerDocument updated) {
        var existing = findByIdOrThrow(id);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt()); // preserva campos imutáveis
        return customerRepository.save(updated);
    }

    // $set — atualiza APENAS um campo sem carregar o documento
    // Mais eficiente que save() pois não trafega o documento inteiro
    public CustomerDocument updatePhone(String id, String phone) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().set("phone", phone);
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    // $set — mesmo padrão, campo diferente
    public CustomerDocument updateStatus(String id, CustomerStatus status) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().set("status", status);
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    // ==================== MEDIUM UPDATE ====================

    // $inc — incrementa atomicamente (positivo = adiciona, negativo = subtrai)
    // Operação atômica: sem race condition mesmo com múltiplas requisições simultâneas
    public CustomerDocument addLoyaltyPoints(String id, int delta) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().inc("loyaltyPoints", delta);
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    // $push — adiciona um elemento ao FINAL do array (permite duplicatas)
    public CustomerDocument addAddress(String id, AddressDocument address) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().push("addresses", address);
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    // $pull — remove todos os elementos do array que casam com a condição
    // Aceita um subdocumento como filtro para arrays de objetos
    public CustomerDocument removeAddressByCity(String id, String city) {
        var query = Query.query(Criteria.where("_id").is(id));
        var update = new Update().pull("addresses", new Document("city", city));
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    // ==================== ADVANCED UPDATE ====================

    // updateMulti — atualiza MÚLTIPLOS documentos numa única operação atômica
    // Retorna a contagem de documentos modificados
    public long suspendInactiveCustomers(int maxPoints) {
        var query = Query.query(
                Criteria.where("status").is(CustomerStatus.INACTIVE)
                        .and("loyaltyPoints").lt(maxPoints)
        );
        var update = new Update().set("status", CustomerStatus.SUSPENDED);
        return mongoTemplate.updateMulti(query, update, CustomerDocument.class).getModifiedCount();
    }

    // Operador posicional $ — atualiza um campo DENTRO do elemento de array que casou na query
    // A query deve conter o filtro do array para "ancorar" o $
    // Ex: addresses.$.zipCode atualiza apenas o endereço cuja cidade casou
    public CustomerDocument updateAddressZip(String id, String city, String newZipCode) {
        var query = Query.query(
                Criteria.where("_id").is(id)
                        .and("addresses.city").is(city) // ancora o operador $
        );
        var update = new Update().set("addresses.$.zipCode", newZipCode);
        mongoTemplate.updateFirst(query, update, CustomerDocument.class);
        return findByIdOrThrow(id);
    }

    private CustomerDocument findByIdOrThrow(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }
}
