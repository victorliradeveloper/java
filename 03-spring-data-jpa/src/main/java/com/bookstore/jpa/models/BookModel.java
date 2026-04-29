package com.bookstore.jpa.models;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "TB_BOOK")
public class BookModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String title;

    //@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToOne//(fetch = FetchType.LAZY)
    // Cria a coluna "publisher_id" na tabela TB_BOOK. Ela armazena o ID do publisher, funcionando como chave estrangeira que aponta para a tabela TB_PUBLISHER.
    @JoinColumn(name = "publisher_id")
    private PublisherModel publisher;

    //@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToMany//(fetch = FetchType.LAZY)
    // Cria uma terceira tabela chamada "tb_book_author" no banco para representar o relacionamento ManyToMany.
    // Isso é necessário pois um livro pode ter vários autores, e um autor pode ter vários livros.
    // Não é possível representar isso com uma simples coluna, então o JPA cria uma tabela intermediária.
    @JoinTable(
            name = "tb_book_author",
            // Coluna que guarda o ID do Book nessa tabela intermediária (aponta para TB_BOOK)
            joinColumns = @JoinColumn(name = "book_id"),
            // Coluna que guarda o ID do Author nessa tabela intermediária (aponta para TB_AUTHOR)
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    private Set<AuthorModel> authors = new HashSet<>();

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL)
    private ReviewModel review;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PublisherModel getPublisher() {
        return publisher;
    }

    public void setPublisher(PublisherModel publisher) {
        this.publisher = publisher;
    }

    public Set<AuthorModel> getAuthors() {
        return authors;
    }

    public void setAuthors(Set<AuthorModel> authors) {
        this.authors = authors;
    }

    public ReviewModel getReview() {
        return review;
    }

    public void setReview(ReviewModel review) {
        this.review = review;
    }
}
