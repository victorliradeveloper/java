package com.bookstore.jpa.services;

import com.bookstore.jpa.dtos.BookRecordDto;
import com.bookstore.jpa.models.BookModel;
import com.bookstore.jpa.models.ReviewModel;
import com.bookstore.jpa.repositories.AuthorRepository;
import com.bookstore.jpa.repositories.BookRepository;
import com.bookstore.jpa.repositories.PublisherRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Marca essa classe como um Service do Spring, ou seja, ela contém as regras de negócio da aplicação.
// O Spring gerencia essa classe automaticamente e permite que ela seja injetada em outras classes (ex: controllers).
@Service
public class BookService {

    // Repositórios são a ponte entre o código Java e o banco de dados.
    // "final" significa que a referência nunca muda depois de definida no construtor.
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    // O Spring injeta os repositórios automaticamente via construtor (Injeção de Dependência).
    // Você não precisa fazer "new BookRepository()" manualmente — o Spring faz isso por você.
    public BookService(BookRepository bookRepository, AuthorRepository authorRepository, PublisherRepository publisherRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.publisherRepository = publisherRepository;
    }

    // Busca todos os livros no banco e retorna como uma lista.
    // O JPA gera o SQL "SELECT * FROM TB_BOOK" automaticamente por baixo dos panos.
    public List<BookModel> getAllBooks() {
        return bookRepository.findAll();
    }

    // Garante que tudo dentro desse método acontece de forma atômica: ou todas as operações no banco
    // são salvas com sucesso, ou nenhuma delas é. Se qualquer erro ocorrer no meio do caminho,
    // o banco desfaz tudo automaticamente (rollback), evitando dados incompletos ou corrompidos.
    @Transactional
    public BookModel saveBook(BookRecordDto bookRecordDto) {
        // Cria um novo objeto Book na memória (ainda não foi salvo no banco).
        BookModel book = new BookModel();

        // Preenche o título com o valor que veio do corpo da requisição HTTP.
        book.setTitle(bookRecordDto.title());

        // Busca o Publisher no banco pelo ID informado e associa ao livro.
        // O .get() obtém o valor do Optional retornado pelo findById.
        book.setPublisher(publisherRepository.findById(bookRecordDto.publisherId()).get());

        // Busca todos os Authors pelos IDs informados, converte para Set e associa ao livro.
        // Set é usado pois não permite autores duplicados no mesmo livro.
        book.setAuthors(authorRepository.findAllById(bookRecordDto.authorIds()).stream().collect(Collectors.toSet()));

        // Cria o Review na memória e preenche com o comentário da requisição.
        ReviewModel reviewModel = new ReviewModel();
        reviewModel.setComment(bookRecordDto.reviewComment());

        // Liga o Review ao Book (necessário para a chave estrangeira book_id na TB_REVIEW).
        reviewModel.setBook(book);

        // Liga o Book ao Review (necessário para o cascade funcionar ao salvar).
        book.setReview(reviewModel);

        // Salva o Book no banco. Por causa do cascade = CascadeType.ALL no BookModel,
        // o Review também é salvo automaticamente junto, sem precisar chamar reviewRepository.save().
        return bookRepository.save(book);
    }

    // Mesmo princípio: garante que a deleção seja completa. Se o Book tiver Review associado,
    // o cascade apaga os dois juntos. Se algo falhar, o banco desfaz tudo (rollback).
    @Transactional
    public void deleteBook(UUID id){
        // Deleta o livro pelo ID. O JPA gera o SQL "DELETE FROM TB_BOOK WHERE id = ?" automaticamente.
        // O cascade remove o Review associado automaticamente também.
        bookRepository.deleteById(id);
    }


}
