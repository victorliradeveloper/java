package com.bookstore.jpa.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "TB_PUBLISHER")
public class PublisherModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    // Oculta o campo "books" no JSON de resposta (GET), mas permite recebê-lo no JSON de entrada (POST/PUT). Evita loop infinito na serialização: Book tem Publisher, Publisher tem Books, Books tem Publisher...
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    // Um publisher pode ter muitos livros. "mappedBy = publisher" indica que o lado Book é o dono do relacionamento (quem tem a coluna publisher_id na tabela).
    @OneToMany(mappedBy = "publisher", fetch = FetchType.LAZY)
    // Coleção dos livros desse publisher. Inicializada como HashSet para evitar NullPointerException ao adicionar itens antes de persistir.
    private Set<BookModel> books = new HashSet<>();


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<BookModel> getBooks() {
        return books;
    }

    public void setBooks(Set<BookModel> books) {
        this.books = books;
    }
}
