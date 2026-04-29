package com.bookstore.mongodb.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

// Documento principal — equivalente ao BookModel do projeto JPA.
// Publisher e Authors são referenciados via @DBRef (armazenados como referências no MongoDB).
// Review é embutida diretamente no documento (não tem collection própria).
@Document(collection = "books")
public class BookDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String title;

    // @DBRef faz o Spring Data MongoDB armazenar a referência ao documento e resolvê-la em tempo de leitura.
    // Equivalente ao @ManyToOne do JPA.
    @DBRef
    private PublisherDocument publisher;

    // Equivalente ao @ManyToMany do JPA — substituiu a tabela tb_book_author por um array de referências.
    @DBRef
    private List<AuthorDocument> authors;

    // Embedded — sem @DBRef. A review é salva dentro do documento book, sem collection separada.
    // Equivalente ao @OneToOne com CascadeType.ALL do JPA.
    private ReviewDocument review;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PublisherDocument getPublisher() {
        return publisher;
    }

    public void setPublisher(PublisherDocument publisher) {
        this.publisher = publisher;
    }

    public List<AuthorDocument> getAuthors() {
        return authors;
    }

    public void setAuthors(List<AuthorDocument> authors) {
        this.authors = authors;
    }

    public ReviewDocument getReview() {
        return review;
    }

    public void setReview(ReviewDocument review) {
        this.review = review;
    }
}
