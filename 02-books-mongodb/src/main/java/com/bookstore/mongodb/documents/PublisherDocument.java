package com.bookstore.mongodb.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Equivalente ao PublisherModel do projeto JPA.
// Não carrega lista de livros — resolvida via bookRepository.findByPublisherId() quando necessário.
@Document(collection = "publishers")
public class PublisherDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
