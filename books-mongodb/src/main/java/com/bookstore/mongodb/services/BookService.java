package com.bookstore.mongodb.services;

import com.bookstore.mongodb.documents.BookDocument;
import com.bookstore.mongodb.documents.ReviewDocument;
import com.bookstore.mongodb.dtos.BookRecordDto;
import com.bookstore.mongodb.repositories.AuthorRepository;
import com.bookstore.mongodb.repositories.BookRepository;
import com.bookstore.mongodb.repositories.PublisherRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Regras de negócio da aplicação. Equivalente ao BookService do projeto JPA,
// mas sem @Transactional — cada operação é atômica em nível de documento no MongoDB.
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    public BookService(BookRepository bookRepository, AuthorRepository authorRepository, PublisherRepository publisherRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.publisherRepository = publisherRepository;
    }

    public List<BookDocument> getAllBooks() {
        return bookRepository.findAll();
    }

    public BookDocument saveBook(BookRecordDto bookRecordDto) {
        BookDocument book = new BookDocument();
        book.setTitle(bookRecordDto.title());

        // Busca o Publisher pelo ID e associa via @DBRef — equivalente ao @ManyToOne do JPA.
        book.setPublisher(publisherRepository.findById(bookRecordDto.publisherId()).orElseThrow());

        // Busca todos os Authors pelos IDs e associa via array de @DBRef — substitui a tabela tb_book_author.
        book.setAuthors(authorRepository.findAllById(bookRecordDto.authorIds()));

        // A review é embutida no documento — sem tabela/collection separada, sem cascade necessário.
        ReviewDocument review = new ReviewDocument();
        review.setComment(bookRecordDto.reviewComment());
        book.setReview(review);

        // Um único save persiste o Book com a review embutida.
        return bookRepository.save(book);
    }

    public void deleteBook(String id) {
        bookRepository.deleteById(id);
    }
}
