package com.bookstore.mongodb.repositories;

import com.bookstore.mongodb.documents.BookDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BookRepository extends MongoRepository<BookDocument, String> {

    List<BookDocument> findByPublisherId(String publisherId);

    List<BookDocument> findByAuthorsId(String authorId);
}
