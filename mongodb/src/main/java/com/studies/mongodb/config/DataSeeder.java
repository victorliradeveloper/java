package com.studies.mongodb.config;

import com.studies.mongodb.documents.CustomerDocument;
import com.studies.mongodb.documents.OrderDocument;
import com.studies.mongodb.documents.ProductDocument;
import com.studies.mongodb.documents.embedded.AddressDocument;
import com.studies.mongodb.documents.embedded.OrderItemDocument;
import com.studies.mongodb.documents.embedded.RatingDocument;
import com.studies.mongodb.enums.CustomerStatus;
import com.studies.mongodb.enums.OrderStatus;
import com.studies.mongodb.repositories.CustomerRepository;
import com.studies.mongodb.repositories.OrderRepository;
import com.studies.mongodb.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public DataSeeder(CustomerRepository customerRepository,
                      ProductRepository productRepository,
                      OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (customerRepository.count() > 0) {
            log.info("Database already seeded — skipping.");
            return;
        }

        var customers = seedCustomers();
        var products  = seedProducts(customers);
        seedOrders(customers, products);

        log.info("Seeded {} customers, {} products and 15 orders.", customers.size(), products.size());
    }

    private List<CustomerDocument> seedCustomers() {
        var alice = customer("Alice Johnson", "alice@email.com", "+55 11 99999-0001",
                List.of(address("Av. Paulista, 1000", "São Paulo", "SP", "01311-100", "Brazil")),
                1500, CustomerStatus.ACTIVE);

        var bob = customer("Bob Smith", "bob@email.com", "+55 21 99999-0002",
                List.of(address("Rua das Flores, 200", "Rio de Janeiro", "RJ", "20040-020", "Brazil")),
                750, CustomerStatus.ACTIVE);

        var carol = customer("Carol White", "carol@email.com", "+1 212 555-0003",
                List.of(address("5th Avenue, 350", "New York", "NY", "10001", "USA")),
                200, CustomerStatus.INACTIVE);

        var david = customer("David Brown", "david@email.com", "+44 20 7946-0004",
                List.of(address("Baker Street, 221B", "London", "England", "NW1 6XE", "UK")),
                50, CustomerStatus.SUSPENDED);

        var eva = customer("Eva Martinez", "eva@email.com", "+55 51 99999-0005",
                List.of(
                        address("Av. Ipiranga, 500", "São Paulo", "SP", "01046-010", "Brazil"),
                        address("Rua dos Andradas, 1234", "Porto Alegre", "RS", "90020-005", "Brazil")
                ),
                3200, CustomerStatus.ACTIVE);

        return customerRepository.saveAll(List.of(alice, bob, carol, david, eva));
    }

    private List<ProductDocument> seedProducts(List<CustomerDocument> customers) {
        var alice = customers.get(0);
        var bob   = customers.get(1);
        var carol = customers.get(2);
        var david = customers.get(3);
        var eva   = customers.get(4);
        var now   = LocalDateTime.now();

        var iphone = product("iPhone 15 Pro", "Apple smartphone with titanium design",
                999.99, 15, "Electronics",
                List.of("smartphone", "apple", "5g"),
                Map.of("color", "space-gray", "storage", "256GB"),
                List.of(
                        rating(alice.getId(), 5, "Amazing phone!", now.minusDays(30)),
                        rating(bob.getId(),   4, "Great but expensive", now.minusDays(20)),
                        rating(carol.getId(), 5, "Best iPhone ever", now.minusDays(10))
                ));

        var samsung = product("Samsung Galaxy S24", "Samsung flagship smartphone",
                849.99, 20, "Electronics",
                List.of("smartphone", "samsung", "5g"),
                Map.of("color", "black", "storage", "128GB"),
                List.of(
                        rating(bob.getId(),   4, "Smooth performance", now.minusDays(25)),
                        rating(david.getId(), 3, "Decent, but I prefer iPhone", now.minusDays(15))
                ));

        var macbook = product("MacBook Pro 14", "Apple laptop with M3 chip",
                1999.99, 8, "Electronics",
                List.of("laptop", "apple", "m3"),
                Map.of("color", "silver", "ram", "16GB"),
                List.of(
                        rating(alice.getId(), 5, "Best laptop I ever used", now.minusDays(45)),
                        rating(eva.getId(),   5, "Incredible performance", now.minusDays(20))
                ));

        var nikeShoes = product("Nike Air Max 2024", "Premium running shoes",
                129.99, 50, "Clothing",
                List.of("shoes", "nike", "running"),
                Map.of("color", "white", "size", "42"),
                List.of(
                        rating(bob.getId(),   4, "Very comfortable", now.minusDays(18)),
                        rating(carol.getId(), 3, "Nice but pricey", now.minusDays(12)),
                        rating(eva.getId(),   4, "Great for long runs", now.minusDays(5))
                ));

        var levisJeans = product("Levi's 501 Jeans", "Classic straight fit jeans",
                79.99, 30, "Clothing",
                List.of("jeans", "levis", "casual"),
                Map.of("color", "blue", "size", "32x32"),
                List.of(
                        rating(alice.getId(), 3, "Good quality, bit stiff at first", now.minusDays(40)),
                        rating(david.getId(), 4, "Classic and very durable", now.minusDays(22))
                ));

        var adidas = product("Adidas Ultraboost 24", "High performance running shoes",
                189.99, 25, "Clothing",
                List.of("shoes", "adidas", "running"),
                Map.of("color", "black", "size", "44"),
                List.of(
                        rating(eva.getId(), 5, "Best shoes I've ever worn", now.minusDays(14)),
                        rating(bob.getId(), 5, "Incredibly comfortable", now.minusDays(8))
                ));

        var cleanCode = product("Clean Code", "A handbook of agile software craftsmanship",
                39.99, 100, "Books",
                List.of("programming", "java", "clean-code"),
                Map.of("author", "Robert C. Martin", "pages", "464"),
                List.of(
                        rating(alice.getId(), 5, "Must read for every developer", now.minusDays(50)),
                        rating(bob.getId(),   5, "Changed the way I code", now.minusDays(35)),
                        rating(carol.getId(), 4, "Excellent, very practical", now.minusDays(28)),
                        rating(eva.getId(),   5, "Essential reading", now.minusDays(15))
                ));

        var designPatterns = product("Design Patterns", "Elements of Reusable Object-Oriented Software",
                44.99, 80, "Books",
                List.of("programming", "patterns", "oop"),
                Map.of("author", "Gang of Four", "pages", "395"),
                List.of(
                        rating(alice.getId(), 4, "Dense but extremely valuable", now.minusDays(60)),
                        rating(bob.getId(),   4, "Good reference book", now.minusDays(42))
                ));

        var effectiveJava = product("Effective Java", "Programming language best practices guide",
                49.99, 60, "Books",
                List.of("programming", "java", "best-practices"),
                Map.of("author", "Joshua Bloch", "pages", "412"),
                List.of(
                        rating(eva.getId(),   5, "Best Java book ever written", now.minusDays(70)),
                        rating(alice.getId(), 5, "Every Java dev should own this", now.minusDays(55))
                ));

        var pragmatic = product("The Pragmatic Programmer", "Your journey to mastery",
                44.99, 90, "Books",
                List.of("programming", "career", "best-practices"),
                Map.of("author", "Dave Thomas & Andy Hunt", "pages", "352"),
                List.of(
                        rating(bob.getId(),   4, "Great career and mindset advice", now.minusDays(33)),
                        rating(carol.getId(), 3, "Decent but a bit dated", now.minusDays(20))
                ));

        return productRepository.saveAll(List.of(
                iphone, samsung, macbook, nikeShoes, levisJeans,
                adidas, cleanCode, designPatterns, effectiveJava, pragmatic
        ));
    }

    private void seedOrders(List<CustomerDocument> customers, List<ProductDocument> products) {
        var alice = customers.get(0);
        var bob   = customers.get(1);
        var carol = customers.get(2);
        var david = customers.get(3);
        var eva   = customers.get(4);

        var iphone         = products.get(0);
        var samsung        = products.get(1);
        var macbook        = products.get(2);
        var nikeShoes      = products.get(3);
        var levisJeans     = products.get(4);
        var adidas         = products.get(5);
        var cleanCode      = products.get(6);
        var designPatterns = products.get(7);
        var effectiveJava  = products.get(8);
        var pragmatic      = products.get(9);

        var now = LocalDateTime.now();

        orderRepository.saveAll(List.of(
                // ---- Alice ----
                order(alice, List.of(item(iphone, 1)),
                        alice.getAddresses().get(0), OrderStatus.DELIVERED, now.minusDays(60)),

                order(alice, List.of(item(cleanCode, 2), item(effectiveJava, 1)),
                        alice.getAddresses().get(0), OrderStatus.DELIVERED, now.minusDays(45)),

                order(alice, List.of(item(macbook, 1)),
                        alice.getAddresses().get(0), OrderStatus.PENDING, now.minusHours(2)),

                // ---- Bob ----
                order(bob, List.of(item(nikeShoes, 1)),
                        bob.getAddresses().get(0), OrderStatus.DELIVERED, now.minusDays(30)),

                order(bob, List.of(item(designPatterns, 1), item(pragmatic, 1)),
                        bob.getAddresses().get(0), OrderStatus.SHIPPED, now.minusDays(5)),

                // Stale PROCESSING — older than 14 days
                order(bob, List.of(item(samsung, 1)),
                        bob.getAddresses().get(0), OrderStatus.PROCESSING, now.minusDays(20)),

                // ---- Carol ----
                order(carol, List.of(item(cleanCode, 1)),
                        carol.getAddresses().get(0), OrderStatus.DELIVERED, now.minusDays(15)),

                order(carol, List.of(item(macbook, 1)),
                        carol.getAddresses().get(0), OrderStatus.CANCELLED, now.minusDays(10)),

                // ---- David ----
                // Stale PENDING — older than 7 days
                order(david, List.of(item(levisJeans, 1)),
                        david.getAddresses().get(0), OrderStatus.PENDING, now.minusDays(15)),

                // ---- Eva ----
                order(eva, List.of(item(iphone, 1), item(cleanCode, 3)),
                        eva.getAddresses().get(0), OrderStatus.DELIVERED, now.minusDays(90)),

                order(eva, List.of(item(macbook, 1), item(adidas, 1)),
                        eva.getAddresses().get(1), OrderStatus.DELIVERED, now.minusDays(60)),

                order(eva, List.of(item(effectiveJava, 2), item(designPatterns, 1)),
                        eva.getAddresses().get(0), OrderStatus.SHIPPED, now.minusDays(7)),

                // Stale PROCESSING — older than 14 days
                order(eva, List.of(item(iphone, 2)),
                        eva.getAddresses().get(0), OrderStatus.PROCESSING, now.minusDays(25)),

                order(eva, List.of(item(nikeShoes, 1), item(adidas, 1)),
                        eva.getAddresses().get(1), OrderStatus.PENDING, now.minusDays(1)),

                order(bob, List.of(item(cleanCode, 1)),
                        bob.getAddresses().get(0), OrderStatus.CANCELLED, now.minusDays(20))
        ));
    }

    // ==================== builders ====================

    private CustomerDocument customer(String name, String email, String phone,
                                      List<AddressDocument> addresses, int loyaltyPoints, CustomerStatus status) {
        var c = new CustomerDocument();
        c.setName(name);
        c.setEmail(email);
        c.setPhone(phone);
        c.setAddresses(addresses);
        c.setLoyaltyPoints(loyaltyPoints);
        c.setStatus(status);
        c.setCreatedAt(LocalDateTime.now().minusMonths(6));
        return c;
    }

    private AddressDocument address(String street, String city, String state, String zipCode, String country) {
        return new AddressDocument(street, city, state, zipCode, country);
    }

    private ProductDocument product(String name, String description, Double price, Integer stock,
                                    String category, List<String> tags, Map<String, String> attributes,
                                    List<RatingDocument> ratings) {
        var p = new ProductDocument();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setStockQuantity(stock);
        p.setCategory(category);
        p.setTags(tags);
        p.setAttributes(attributes);
        p.setRatings(ratings);
        p.setCreatedAt(LocalDateTime.now().minusMonths(3));
        return p;
    }

    private RatingDocument rating(String userId, int score, String comment, LocalDateTime createdAt) {
        return new RatingDocument(userId, score, comment, createdAt);
    }

    private OrderDocument order(CustomerDocument customer, List<OrderItemDocument> items,
                                AddressDocument shippingAddress, OrderStatus status, LocalDateTime createdAt) {
        var o = new OrderDocument();
        o.setCustomer(customer);
        o.setItems(items);
        o.setShippingAddress(shippingAddress);
        o.setStatus(status);
        o.setTotalAmount(items.stream()
                .mapToDouble(i -> i.unitPrice() * i.quantity())
                .sum());
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        return o;
    }

    private OrderItemDocument item(ProductDocument product, int quantity) {
        return new OrderItemDocument(product.getId(), product.getName(), product.getPrice(), quantity);
    }
}
